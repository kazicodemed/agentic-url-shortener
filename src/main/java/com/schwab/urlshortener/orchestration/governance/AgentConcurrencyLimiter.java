package com.schwab.urlshortener.orchestration.governance;

import com.schwab.urlshortener.orchestration.config.AgentConcurrencyProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guardrail that bounds concurrent agent (LLM) calls.
 *
 * <p>Two limits apply to every call, in this order:
 * <ol>
 *   <li><b>Per-run</b>: one orchestration run cannot use more than
 *       {@code maxConcurrentCallsPerRun} permits, so a large DAG fan-out
 *       cannot starve other runs.</li>
 *   <li><b>Global</b>: total in-flight agent calls never exceed
 *       {@code maxConcurrentCalls}, keeping us under LLM rate limits and cost.</li>
 * </ol>
 * The per-run permit is taken first so a run that is already at its own limit
 * never holds a global permit while it waits.
 *
 * <p>Both waits share one deadline ({@code acquireTimeout}). On timeout the call
 * is rejected with {@link AgentConcurrencyLimitExceededException}, which the
 * engine treats as retryable. Permits are always released, even if the call throws.
 *
 * <p>Uses {@link Semaphore} (no {@code synchronized}), so callers on virtual
 * threads are not pinned to carrier threads while they wait.
 */
@Slf4j
@Component
public class AgentConcurrencyLimiter {

    private final AgentConcurrencyProperties properties;
    private final Semaphore globalPermits;
    private final Map<String, Semaphore> runPermits = new ConcurrentHashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger();

    private final Timer permitWaitTimer;
    private final Counter rejectedRun;
    private final Counter rejectedGlobal;

    public AgentConcurrencyLimiter(AgentConcurrencyProperties properties, MeterRegistry meterRegistry) {
        this.properties = Objects.requireNonNull(properties);
        this.globalPermits = new Semaphore(properties.maxConcurrentCalls(), true);

        Gauge.builder("orchestration.agent.calls.in_flight", inFlight, AtomicInteger::get)
                .description("Agent calls currently executing")
                .register(meterRegistry);
        this.permitWaitTimer = Timer.builder("orchestration.agent.permit.wait")
                .description("Time spent waiting for an agent concurrency permit")
                .register(meterRegistry);
        this.rejectedRun = Counter.builder("orchestration.agent.calls.rejected")
                .tag("scope", "run")
                .description("Agent calls rejected because the run limit was saturated")
                .register(meterRegistry);
        this.rejectedGlobal = Counter.builder("orchestration.agent.calls.rejected")
                .tag("scope", "global")
                .description("Agent calls rejected because the global limit was saturated")
                .register(meterRegistry);

        log.info("Agent concurrency limits: global={}, perRun={}, acquireTimeout={}",
                properties.maxConcurrentCalls(), properties.maxConcurrentCallsPerRun(),
                properties.acquireTimeout());
    }

    /**
     * Run an agent call under the concurrency limits.
     *
     * @param runId     orchestration run the call belongs to
     * @param agentName agent making the call (for logs and audit)
     * @param call      the actual agent/LLM work
     * @return the call's result
     * @throws AgentConcurrencyLimitExceededException if no permit was available in time (retryable)
     * @throws InterruptedException                   if the waiting thread was interrupted
     * @throws Exception                              whatever the call itself throws
     */
    public <T> T execute(String runId, String agentName, Callable<T> call) throws Exception {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(call, "call");

        long timeoutNanos = properties.acquireTimeout().toNanos();
        long start = System.nanoTime();
        long deadline = start + timeoutNanos;

        Semaphore runSemaphore = runPermits.computeIfAbsent(
                runId, id -> new Semaphore(properties.maxConcurrentCallsPerRun(), true));

        if (!runSemaphore.tryAcquire(timeoutNanos, TimeUnit.NANOSECONDS)) {
            rejectedRun.increment();
            log.warn("Agent call rejected: run limit saturated (run={}, agent={})", runId, agentName);
            throw new AgentConcurrencyLimitExceededException(
                    AgentConcurrencyLimitExceededException.Scope.RUN, runId, agentName, properties.acquireTimeout());
        }
        try {
            long remaining = Math.max(0, deadline - System.nanoTime());
            if (!globalPermits.tryAcquire(remaining, TimeUnit.NANOSECONDS)) {
                rejectedGlobal.increment();
                log.warn("Agent call rejected: global limit saturated (run={}, agent={})", runId, agentName);
                throw new AgentConcurrencyLimitExceededException(
                        AgentConcurrencyLimitExceededException.Scope.GLOBAL, runId, agentName,
                        properties.acquireTimeout());
            }
            try {
                permitWaitTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                inFlight.incrementAndGet();
                log.debug("Agent call started (run={}, agent={}, inFlight={})", runId, agentName, inFlight.get());
                return call.call();
            } finally {
                inFlight.decrementAndGet();
                globalPermits.release();
            }
        } finally {
            runSemaphore.release();
        }
    }

    /**
     * Forget a finished run's per-run semaphore. Call when the run reaches a
     * terminal state (completed, failed, rolled back, cancelled).
     */
    public void releaseRun(String runId) {
        runPermits.remove(runId);
    }

    /** Agent calls currently executing. */
    public int inFlight() {
        return inFlight.get();
    }

    /** Global permits currently free. */
    public int availableGlobalPermits() {
        return globalPermits.availablePermits();
    }
}

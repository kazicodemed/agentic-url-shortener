package com.schwab.urlshortener.orchestration.governance;

import com.schwab.urlshortener.orchestration.config.AgentConcurrencyProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentConcurrencyLimiterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private AgentConcurrencyLimiter limiter(int global, int perRun, Duration timeout) {
        return new AgentConcurrencyLimiter(new AgentConcurrencyProperties(global, perRun, timeout), registry);
    }

    @Test
    void neverExceedsGlobalLimitAcrossRuns() throws Exception {
        AgentConcurrencyLimiter limiter = limiter(3, 3, Duration.ofSeconds(10));
        ConcurrencyProbe probe = new ConcurrencyProbe();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String runId = "run-" + (i % 5);
                futures.add(pool.submit(() -> limiter.execute(runId, "code-agent", probe::work)));
            }
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(probe.maxObserved()).isEqualTo(3);
        assertThat(limiter.inFlight()).isZero();
        assertThat(limiter.availableGlobalPermits()).isEqualTo(3);
    }

    @Test
    void perRunLimitCapsASingleRunsFanOut() throws Exception {
        AgentConcurrencyLimiter limiter = limiter(4, 2, Duration.ofSeconds(10));
        ConcurrencyProbe probe = new ConcurrencyProbe();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> limiter.execute("run-A", "test-agent", probe::work)));
            }
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(probe.maxObserved()).isEqualTo(2);
    }

    @Test
    void saturatedRunDoesNotBlockOtherRuns() throws Exception {
        AgentConcurrencyLimiter limiter = limiter(4, 2, Duration.ofSeconds(10));
        CountDownLatch runAHolding = new CountDownLatch(2);
        CountDownLatch releaseRunA = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> limiter.execute("run-A", "code-agent", () -> {
                    runAHolding.countDown();
                    releaseRunA.await();
                    return null;
                }));
            }
            assertThat(runAHolding.await(5, TimeUnit.SECONDS)).isTrue();

            // run-A is at its per-run limit, but run-B still gets a global permit immediately.
            String result = pool.submit(() -> limiter.execute("run-B", "docs-agent", () -> "done"))
                    .get(1, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("done");

            releaseRunA.countDown();
        }
    }

    @Test
    void rejectsWithRetryableExceptionWhenGlobalLimitStaysSaturated() throws Exception {
        AgentConcurrencyLimiter limiter = limiter(1, 1, Duration.ofMillis(100));
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            pool.submit(() -> limiter.execute("run-A", "code-agent", () -> {
                holding.countDown();
                release.await();
                return null;
            }));
            assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> limiter.execute("run-B", "code-agent", () -> "never"))
                    .isInstanceOf(AgentConcurrencyLimitExceededException.class)
                    .satisfies(e -> {
                        var ex = (AgentConcurrencyLimitExceededException) e;
                        assertThat(ex.getScope()).isEqualTo(AgentConcurrencyLimitExceededException.Scope.GLOBAL);
                        assertThat(ex.getRunId()).isEqualTo("run-B");
                    });

            release.countDown();
        }

        assertThat(registry.get("orchestration.agent.calls.rejected").tag("scope", "global").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void rejectsWithRunScopeWhenRunLimitStaysSaturated() throws Exception {
        AgentConcurrencyLimiter limiter = limiter(4, 1, Duration.ofMillis(100));
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            pool.submit(() -> limiter.execute("run-A", "code-agent", () -> {
                holding.countDown();
                release.await();
                return null;
            }));
            assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> limiter.execute("run-A", "test-agent", () -> "never"))
                    .isInstanceOf(AgentConcurrencyLimitExceededException.class)
                    .extracting(e -> ((AgentConcurrencyLimitExceededException) e).getScope())
                    .isEqualTo(AgentConcurrencyLimitExceededException.Scope.RUN);

            // the rejected run call must not have consumed a global permit
            assertThat(limiter.availableGlobalPermits()).isEqualTo(3);
            release.countDown();
        }
    }

    @Test
    void releasesPermitsWhenTheCallFails() throws Exception {
        AgentConcurrencyLimiter limiter = limiter(1, 1, Duration.ofMillis(100));

        assertThatThrownBy(() -> limiter.execute("run-A", "code-agent", () -> {
            throw new IllegalStateException("LLM returned garbage");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(limiter.inFlight()).isZero();
        assertThat(limiter.availableGlobalPermits()).isEqualTo(1);
        // the same run can immediately make another call
        assertThat(limiter.execute("run-A", "code-agent", () -> "ok")).isEqualTo("ok");
    }

    @Test
    void rejectsPerRunLimitAboveGlobalLimit() {
        assertThatThrownBy(() -> new AgentConcurrencyProperties(2, 3, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-concurrent-calls-per-run");
    }

    /** Tracks the highest number of calls running at the same moment. */
    private static final class ConcurrencyProbe {
        private final AtomicInteger current = new AtomicInteger();
        private final AtomicInteger max = new AtomicInteger();

        Void work() throws InterruptedException {
            int now = current.incrementAndGet();
            max.accumulateAndGet(now, Math::max);
            Thread.sleep(50);
            current.decrementAndGet();
            return null;
        }

        int maxObserved() {
            return max.get();
        }
    }
}

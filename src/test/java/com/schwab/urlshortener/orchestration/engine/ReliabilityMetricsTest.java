package com.schwab.urlshortener.orchestration.engine;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import com.schwab.urlshortener.orchestration.execution.TaskExecutor;
import com.schwab.urlshortener.orchestration.execution.TaskResult;
import com.schwab.urlshortener.orchestration.run.RunStatus;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A run fails, the same requirement is run again and succeeds (recovery), and a third run
 * waits for a human. Checks every metric the brief names against that known history.
 */
@SpringBootTest
@Transactional
class ReliabilityMetricsTest {

    private static final String GREENFIELD = "Add an endpoint that returns total clicks for a short code";

    @Autowired
    private OrchestrationEngine engine;
    @Autowired
    private ReliabilityMetricsService metricsService;
    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private TaskExecutor taskExecutor;

    private final AtomicBoolean failT2 = new AtomicBoolean(true);

    @BeforeEach
    void workerThatFailsT2UntilFixed() {
        when(taskExecutor.execute(any())).thenAnswer(inv -> {
            PlannedTask task = inv.getArgument(0, PlannedTask.class);
            return task.id().equals("T2") && failT2.get()
                    ? TaskResult.failed("T2", "simulated failure")
                    : TaskResult.ok(task.id(), "done");
        });
    }

    @Test
    void metricsReflectFailureRecoveryAndHumanWait() throws InterruptedException {
        assertThat(engine.startRun(GREENFIELD).getStatus()).isEqualTo(RunStatus.FAILED);   // T2 fails → rollback
        entityManager.flush();       // in production each run commits on its own; here, flush to stamp updatedAt now

        Thread.sleep(50);            // a measurable gap between failure and recovery
        failT2.set(false);           // "the fix is deployed"
        assertThat(engine.startRun(GREENFIELD).getStatus()).isEqualTo(RunStatus.COMPLETED);
        entityManager.flush();

        assertThat(engine.startRun("Make the system more scalable").getStatus()).isEqualTo(RunStatus.AWAITING_HUMAN);

        ReliabilityMetrics m = metricsService.compute();

        assertThat(m.totalRuns()).isEqualTo(3);
        assertThat(m.completed()).isEqualTo(1);
        assertThat(m.failed()).isEqualTo(1);
        assertThat(m.awaitingHuman()).isEqualTo(1);
        assertThat(m.successRate()).isEqualTo(0.5);                 // 1 completed / 2 finished
        assertThat(m.rollbackFrequency()).isEqualTo(0.5);           // 1 of 2 finished runs rolled back
        assertThat(m.retryRate()).isEqualTo(0.0);                   // stubs always pass first time
        assertThat(m.mttrSeconds()).isNotNull().isGreaterThan(0.0); // failure → next success
        assertThat(m.avgEndToEndSeconds()).isNotNull().isGreaterThanOrEqualTo(0.0);

        // the same story is visible live in Micrometer / Prometheus
        assertThat(meterRegistry.counter("orchestration.execution.rollbacks").count()).isGreaterThanOrEqualTo(1.0);
        assertThat(meterRegistry.find("orchestration.stage.duration").tag("stage", "EXECUTE").timer())
                .isNotNull()
                .satisfies(t -> assertThat(t.count()).isGreaterThanOrEqualTo(2));
    }

    @Test
    void metricsAreEmptyNotZeroWhenNothingHasRun() {
        ReliabilityMetrics m = metricsService.compute();

        assertThat(m.totalRuns()).isZero();
        assertThat(m.successRate()).isNull();     // "no data" is not the same as "0% success"
        assertThat(m.mttrSeconds()).isNull();
    }
}

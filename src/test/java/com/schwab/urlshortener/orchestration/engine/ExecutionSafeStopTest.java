package com.schwab.urlshortener.orchestration.engine;

import com.schwab.urlshortener.orchestration.execution.TaskExecutor;
import com.schwab.urlshortener.orchestration.execution.TaskResult;
import com.schwab.urlshortener.orchestration.run.OrchestrationRun;
import com.schwab.urlshortener.orchestration.run.RunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End to end with a worker that fails T2 of the caching plan:
 * T1 → (T2 fails, T3 finishes) → T4 never starts. The run must stop safely.
 */
@SpringBootTest
@Transactional
class ExecutionSafeStopTest {

    @Autowired
    private OrchestrationEngine engine;

    @MockitoBean
    private TaskExecutor taskExecutor;   // replaces StubTaskExecutor for this test class only

    @BeforeEach
    void failT2() {
        when(taskExecutor.execute(any())).thenAnswer(inv -> {
            var task = inv.getArgument(0, com.schwab.urlshortener.orchestration.agent.design.PlannedTask.class);
            return task.id().equals("T2")
                    ? TaskResult.failed("T2", "simulated failure")
                    : TaskResult.ok(task.id(), "done " + task.id());
        });
    }

    @Test
    void failedTaskSkipsDependentsAndRollsBackCompletedWork() {
        OrchestrationRun run = engine.startRun("Make the system more scalable");
        engine.approve(run.getId(), "kazi", "Plan for 5,000 redirects per second");

        RunView view = engine.getRun(run.getId());

        assertThat(view.status()).isEqualTo(RunStatus.FAILED.name());
        assertThat(view.tasks())
                .filteredOn(t -> t.taskType().startsWith("EXECUTE"))
                .extracting(t -> t.taskType() + ":" + t.status())
                .containsExactlyInAnyOrder(
                        "EXECUTE T1:ROLLED_BACK",   // completed, then undone
                        "EXECUTE T2:FAILED",        // the failure
                        "EXECUTE T3:ROLLED_BACK",   // same wave as T2: finished, then undone
                        "EXECUTE T4:SKIPPED");      // never started
        assertThat(view.auditTrail())
                .anySatisfy(t -> assertThat(t.reason()).startsWith("safe-stop: [T2] failed; skipped [T4]"));
    }
}

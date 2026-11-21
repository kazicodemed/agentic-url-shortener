package com.schwab.urlshortener.orchestration.run;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence of runs, tasks and the audit trail against a real (H2) database.
 * Each test runs in a transaction that is rolled back, so tests never see each other's rows.
 */
@SpringBootTest
@Transactional
class RunPersistenceTest {

    @Autowired
    private OrchestrationRunRepository orchestrationRunRepository;
    @Autowired
    private TaskExecutionRepository taskExecutionRepository;
    @Autowired
    private StateTransitionRepository stateTransitionRepository;

    @Test
    void savesRunWithDefaultsAndLinksTaskAndTransition() {
        // status deliberately not set: @PrePersist must default it to RUNNING
        OrchestrationRun run = orchestrationRunRepository.save(OrchestrationRun.builder()
                .rawRequirement("give me value for stock sndsk")
                .build());

        assertNotNull(run.getId());
        assertEquals("give me value for stock sndsk", run.getRawRequirement());
        assertEquals(RunStatus.RUNNING, run.getStatus());
        assertNotNull(run.getCreatedAt());

        TaskExecution task = new TaskExecution();
        task.setAttempts(1);
        task.setRun(run);
        task.setStatus(TaskStatus.RUNNING);
        task = taskExecutionRepository.save(task);

        assertNotNull(task.getId());
        assertNotNull(task.getUpdatedAt());
        assertEquals(1, taskExecutionRepository.findByRunId(run.getId()).size());

        StateTransition transition = new StateTransition();
        transition.setToStatus("RUNNING");
        transition.setRun(run);
        transition.setTask(task);
        transition = stateTransitionRepository.save(transition);

        assertNotNull(transition.getId());
        assertNotNull(transition.getOccurredAt());   // set by @PrePersist
    }

    @Test
    void auditTrailIsOrderedByTimeAndScopedByTask() {
        OrchestrationRun run = orchestrationRunRepository.save(OrchestrationRun.builder()
                .rawRequirement("give me value for stock MU")
                .build());

        TaskExecution task = new TaskExecution();
        task.setAttempts(1);
        task.setRun(run);
        task.setStatus(TaskStatus.RUNNING);
        task = taskExecutionRepository.save(task);

        LocalDateTime t0 = LocalDateTime.now();
        StateTransition runCreated = StateTransition.builder()
                .run(run).task(null)                      // run-level, no task
                .toStatus("RUNNING").reason("run created")
                .occurredAt(t0).build();
        StateTransition taskStarted = StateTransition.builder()
                .run(run).task(task)
                .fromStatus("PENDING").toStatus("RUNNING").reason("requirements task started")
                .occurredAt(t0.plusSeconds(1)).build();

        // saved newest first on purpose: the query must sort by occurredAt, not by insert order
        stateTransitionRepository.saveAll(List.of(taskStarted, runCreated));

        var runTrail = stateTransitionRepository.findByRunIdOrderByOccurredAtAsc(run.getId());
        assertEquals(2, runTrail.size());
        assertEquals("run created", runTrail.get(0).getReason());
        assertEquals("requirements task started", runTrail.get(1).getReason());

        var taskTrail = stateTransitionRepository.findByTaskIdOrderByOccurredAtAsc(task.getId());
        assertEquals(1, taskTrail.size());            // the run-level transition is excluded
        assertEquals("requirements task started", taskTrail.get(0).getReason());
    }

}

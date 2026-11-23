package com.schwab.urlshortener.orchestration.execution;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orchestration.agents.mode", havingValue = "stub", matchIfMissing = true)
public class StubTaskExecutor implements TaskExecutor {          // ← add "implements TaskExecutor"

    static final long WORK_MILLIS = 200;

    @Override
    public TaskResult execute(PlannedTask task) {
        try {
            Thread.sleep(WORK_MILLIS);                           // pretend to work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();                  // put the signal back for the caller
            return TaskResult.failed(task.id(), "interrupted");
        }

        if (task.title().contains("[fail]")) {
            return TaskResult.failed(task.id(), "simulated failure: " + task.title());
        }
        return TaskResult.ok(task.id(), "simulated change for " + task.id() + ": " + task.title());
    }
}
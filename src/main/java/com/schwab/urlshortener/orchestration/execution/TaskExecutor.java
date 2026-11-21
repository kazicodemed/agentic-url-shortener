package com.schwab.urlshortener.orchestration.execution;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;

public interface TaskExecutor {
    TaskResult execute(PlannedTask task);
}

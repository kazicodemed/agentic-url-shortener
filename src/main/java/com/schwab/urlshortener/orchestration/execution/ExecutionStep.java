package com.schwab.urlshortener.orchestration.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
public class ExecutionStep {
    private final TaskExecutor taskExecutor;
    //virtual-thread executor
    private final Executor orchestrationExecutor;

    public ExecutionStep(TaskExecutor taskExecutor,@Qualifier("orchestrationExecutor") Executor orchestrationExecutor) {
        this.taskExecutor = taskExecutor;
        this.orchestrationExecutor = orchestrationExecutor;
    }
}

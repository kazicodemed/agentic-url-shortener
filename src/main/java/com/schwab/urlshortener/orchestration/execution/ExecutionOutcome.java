package com.schwab.urlshortener.orchestration.execution;

import java.util.List;

public record ExecutionOutcome(List<TaskResult> results, List<String> skippedTaskIds) {

    public boolean succeeded() {
        for (TaskResult result : results) {
            if (!result.success()) {
                return false;
            }
        }
        return true;
    }
}

package com.schwab.urlshortener.orchestration.execution;

public record TaskResult(String taskId, boolean success, String output, String error) {

    public static TaskResult ok(String taskId, String output) {
        return new TaskResult(taskId, true, output, null);
    }

    public static TaskResult failed(String taskId, String error) {
        return new TaskResult(taskId, false, null, error);
    }
}

package com.schwab.urlshortener.orchestration.execution;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class ExecutionStep {
    private final TaskExecutor taskExecutor;
    //virtual-thread executor
    private final Executor orchestrationExecutor;
    public ExecutionStep(TaskExecutor taskExecutor,
                         @Qualifier("orchestrationExecutor") Executor orchestrationExecutor) {
        this.taskExecutor = taskExecutor;
        this.orchestrationExecutor = orchestrationExecutor;
    }

    public ExecutionOutcome run(List<PlannedTask> tasks) {
        List<TaskResult> results = new ArrayList<>();
        List<List<PlannedTask>> waves = DagScheduler.waves(tasks);

        for (int i = 0; i < waves.size(); i++) {
            List<PlannedTask> wave = waves.get(i);

            // 1. start every task in this wave on its own virtual thread
            List<CompletableFuture<TaskResult>> running = wave.stream()
                    .map(task -> CompletableFuture
                            .supplyAsync(() -> taskExecutor.execute(task), orchestrationExecutor)
                            .exceptionally(ex -> TaskResult.failed(task.id(), "worker crashed: " + ex.getMessage())))
                    .toList();

            // synchronization: wait until the WHOLE wave is done
            CompletableFuture.allOf(running.toArray(CompletableFuture[]::new)).join();

            List<TaskResult> waveResults = new ArrayList<>();

            for (CompletableFuture<TaskResult> future : running) {
                waveResults.add(future.join());
            }

            // every task in this wave ran (successfully or not), so record them all first
            results.addAll(waveResults);

            // safe-stop: if anything in this wave failed, don't start the later waves
            if (waveResults.stream().anyMatch(tr -> !tr.success())) {
                List<String> skipped = waves.subList(i + 1, waves.size()).stream()
                        .flatMap(List::stream)
                        .map(PlannedTask::id)
                        .toList();
                //Fail-fast
                return new ExecutionOutcome(results, skipped);
            }

        }
        return new ExecutionOutcome(results, List.of());
    }
}


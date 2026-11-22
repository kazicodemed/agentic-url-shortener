package com.schwab.urlshortener.orchestration.execution;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test with a real virtual-thread executor: proves parallelism, synchronization and safe-stop. */
class ExecutionStepTest {

    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void shutdown() {
        virtualThreads.shutdown();
    }

    private static PlannedTask task(String id, String... dependsOn) {
        return new PlannedTask(id, "title " + id, "desc", List.of(dependsOn));
    }

    /** T1 → (T2, T3) → T4: the caching plan's shape. */
    private static final List<PlannedTask> DIAMOND = List.of(
            task("T1"), task("T2", "T1"), task("T3", "T1"), task("T4", "T2", "T3"));

    private record Span(Instant start, Instant end) {}

    /** A worker that sleeps and records exactly when each task started and ended (thread-safe map). */
    private static TaskExecutor timedWorker(Map<String, Span> spans, String failingId) {
        return task -> {
            Instant start = Instant.now();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return TaskResult.failed(task.id(), "interrupted");
            }
            spans.put(task.id(), new Span(start, Instant.now()));
            return task.id().equals(failingId)
                    ? TaskResult.failed(task.id(), "boom")
                    : TaskResult.ok(task.id(), "done " + task.id());
        };
    }

    @Test
    void allTasksSucceedInDependencyOrder() {
        ExecutionOutcome outcome = new ExecutionStep(timedWorker(new ConcurrentHashMap<>(), null), virtualThreads)
                .run(DIAMOND);

        assertThat(outcome.succeeded()).isTrue();
        assertThat(outcome.skippedTaskIds()).isEmpty();
        assertThat(outcome.results()).extracting(TaskResult::taskId).containsExactly("T1", "T2", "T3", "T4");
    }

    @Test
    void sameWaveRunsInParallelAndNextWaveWaitsForBoth() {
        Map<String, Span> spans = new ConcurrentHashMap<>();
        new ExecutionStep(timedWorker(spans, null), virtualThreads).run(DIAMOND);

        Span t2 = spans.get("T2"), t3 = spans.get("T3"), t4 = spans.get("T4");

        // parallel: T2 and T3 overlap in time (each started before the other ended)
        assertThat(t2.start()).isBefore(t3.end());
        assertThat(t3.start()).isBefore(t2.end());

        // synchronization: T4 starts only after BOTH T2 and T3 have ended
        assertThat(t4.start()).isAfterOrEqualTo(t2.end());
        assertThat(t4.start()).isAfterOrEqualTo(t3.end());
    }

    @Test
    void failureStopsSafelyNeighbourFinishesAndLaterWavesAreSkipped() {
        ExecutionOutcome outcome = new ExecutionStep(timedWorker(new ConcurrentHashMap<>(), "T2"), virtualThreads)
                .run(DIAMOND);

        assertThat(outcome.succeeded()).isFalse();
        // T3 was in the same wave as T2, so it still ran to completion
        assertThat(outcome.results()).extracting(r -> r.taskId() + ":" + r.success())
                .containsExactly("T1:true", "T2:false", "T3:true");
        // T4 never started
        assertThat(outcome.skippedTaskIds()).containsExactly("T4");
    }

    @Test
    void aWorkerThatThrowsBecomesAFailedResultInsteadOfCrashingTheWave() {
        TaskExecutor throwsOnT1 = task -> {
            if (task.id().equals("T1")) throw new IllegalStateException("disk full");
            return TaskResult.ok(task.id(), "done");
        };

        ExecutionOutcome outcome = new ExecutionStep(throwsOnT1, virtualThreads).run(DIAMOND);

        assertThat(outcome.results()).singleElement().satisfies(r -> {
            assertThat(r.success()).isFalse();
            assertThat(r.error()).contains("worker crashed").contains("disk full");
        });
        assertThat(outcome.skippedTaskIds()).containsExactly("T2", "T3", "T4");
    }
}

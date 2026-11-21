package com.schwab.urlshortener.orchestration.execution;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test: grouping a plan into waves is pure logic (no threads, no Spring). */
class DagSchedulerTest {

    private static PlannedTask task(String id, String... dependsOn) {
        return new PlannedTask(id, "title " + id, "desc " + id, List.of(dependsOn));
    }

    /** Waves as ids, so assertions read like the diagram. */
    private static List<List<String>> ids(List<List<PlannedTask>> waves) {
        return waves.stream().map(wave -> wave.stream().map(PlannedTask::id).toList()).toList();
    }

    @Test
    void diamondGivesThreeWavesWithTheMiddleOneParallel() {
        // T1 → T2, T1 → T3, (T2, T3) → T4 — listed shuffled on purpose: list order must not matter
        var waves = DagScheduler.waves(List.of(
                task("T4", "T2", "T3"), task("T1"), task("T3", "T1"), task("T2", "T1")));

        assertThat(ids(waves)).containsExactly(
                List.of("T1"),
                List.of("T2", "T3"),      // can run in parallel
                List.of("T4"));           // waits for both: the synchronization point
    }

    @Test
    void independentTasksShareOneWave() {
        assertThat(ids(DagScheduler.waves(List.of(task("T2"), task("T1")))))
                .containsExactly(List.of("T1", "T2"));
    }

    @Test
    void chainGivesOneTaskPerWave() {
        assertThat(ids(DagScheduler.waves(List.of(task("T3", "T2"), task("T1"), task("T2", "T1")))))
                .containsExactly(List.of("T1"), List.of("T2"), List.of("T3"));
    }

    @Test
    void cachingPlanFromTheDesignStageRunsT2AndT3TogetherBeforeT4() {
        // the exact plan StubDesignAgent produces for "5,000 redirects per second"
        var waves = DagScheduler.waves(List.of(
                task("T1"), task("T2", "T1"), task("T3", "T1"), task("T4", "T2", "T3")));

        assertThat(waves).hasSize(3);
        assertThat(ids(waves).get(1)).containsExactly("T2", "T3");
    }

    @Test
    void everyTaskAppearsExactlyOnce() {
        var tasks = List.of(task("T1"), task("T2", "T1"), task("T3"), task("T4", "T2", "T3"), task("T5", "T1"));

        assertThat(DagScheduler.waves(tasks).stream().flatMap(List::stream).map(PlannedTask::id))
                .containsExactlyInAnyOrder("T1", "T2", "T3", "T4", "T5");
    }
}

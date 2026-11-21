package com.schwab.urlshortener.orchestration.agent.design;

import com.schwab.urlshortener.orchestration.agent.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test: the validator is pure logic, so no Spring context is needed. */
class DesignSpecValidatorTest {

    private final DesignSpecValidator validator = new DesignSpecValidator();

    private static PlannedTask task(String id, String... dependsOn) {
        return new PlannedTask(id, "title " + id, "desc " + id, List.of(dependsOn));
    }

    private static DesignSpec plan(PlannedTask... tasks) {
        // Arrays.asList (not List.of) so a test can include a null task
        return new DesignSpec("Cache redirects in Redis", Arrays.asList(tasks));
    }

    @Test
    void validChainPasses() {
        ValidationResult result = validator.validate(plan(
                task("T3", "T2"),          // listed first on purpose: list order must not matter
                task("T1"),
                task("T2", "T1")));

        assertThat(result.isValid()).isTrue();
        assertThat(result.needsHuman()).isFalse();
    }

    @Test
    void diamondShapedPlanPasses() {
        // T1 → T2, T1 → T3, T2 + T3 → T4
        ValidationResult result = validator.validate(plan(
                task("T1"), task("T2", "T1"), task("T3", "T1"), task("T4", "T2", "T3")));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void blankApproachIsAnError() {
        ValidationResult result = validator.validate(new DesignSpec(" ", List.of(task("T1"))));

        assertThat(result.errors()).singleElement().asString().startsWith("approach is missing");
    }

    @Test
    void nullOrEmptyTasksIsAnError() {
        assertThat(validator.validate(new DesignSpec("approach", null)).errors())
                .containsExactly("tasks is missing or empty: return at least one task");
        assertThat(validator.validate(new DesignSpec("approach", List.of())).errors())
                .containsExactly("tasks is missing or empty: return at least one task");
    }

    @Test
    void nullTaskEntryIsReportedWithoutCrashing() {
        ValidationResult result = validator.validate(plan(task("T1"), null));

        assertThat(result.errors()).containsExactly("tasks[1] must not be null");
    }

    @Test
    void blankIdTitleAndNullDependsOnAreReported() {
        ValidationResult result = validator.validate(plan(new PlannedTask(" ", "", "desc", null)));

        assertThat(result.errors()).containsExactly(
                "tasks[0].id must not be blank",
                "tasks[0].title must not be blank",
                "tasks[0].dependsOn must not be null; use [] if there are none");
    }

    @Test
    void tooManyTasksIsAnError() {
        List<PlannedTask> tasks = new ArrayList<>();
        IntStream.rangeClosed(1, DesignSpecValidator.MAX_TASKS + 1).forEach(i -> tasks.add(task("T" + i)));

        ValidationResult result = validator.validate(new DesignSpec("approach", tasks));

        assertThat(result.errors()).singleElement().asString().startsWith("too many tasks: 11 (max 10)");
    }

    @Test
    void duplicateIdIsAnError() {
        ValidationResult result = validator.validate(plan(task("T1"), task("T1")));

        assertThat(result.errors()).containsExactly("tasks[1]: duplicate task id T1");
    }

    @Test
    void unknownDependencyIsAnError() {
        ValidationResult result = validator.validate(plan(task("T1"), task("T2", "T7")));

        assertThat(result.errors()).containsExactly("tasks[1] (T2) depends on unknown task T7");
    }

    @Test
    void selfDependencyIsAnError() {
        ValidationResult result = validator.validate(plan(task("T1", "T1")));

        assertThat(result.errors()).containsExactly("tasks[0] (T1) depends on itself");
    }

    @Test
    void cycleIsDetectedAndNamesTheStuckTasks() {
        // T1 is fine; T2 and T3 wait on each other forever
        ValidationResult result = validator.validate(plan(task("T1"), task("T2", "T3"), task("T3", "T2")));

        assertThat(result.errors()).containsExactly(
                "cycle among tasks: T2, T3; remove one dependency to break it");
    }

    @Test
    void longerCycleIsDetected() {
        // T1 → T2 → T3 → T1, plus T4 that depends on the cycle (also stuck)
        ValidationResult result = validator.validate(plan(
                task("T1", "T3"), task("T2", "T1"), task("T3", "T2"), task("T4", "T1")));

        assertThat(result.errors()).containsExactly(
                "cycle among tasks: T1, T2, T3, T4; remove one dependency to break it");
    }
}

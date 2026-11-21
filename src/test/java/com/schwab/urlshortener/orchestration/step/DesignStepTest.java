package com.schwab.urlshortener.orchestration.step;

import com.schwab.urlshortener.orchestration.agent.design.DesignAgent;
import com.schwab.urlshortener.orchestration.agent.design.DesignSpec;
import com.schwab.urlshortener.orchestration.agent.design.DesignSpecValidator;
import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DesignStepTest {

    private final RequirementSpec spec = new RequirementSpec("Show clicks", List.of("GET /stats"), List.of());

    private static final DesignSpec VALID = new DesignSpec("Direct implementation", List.of(
            new PlannedTask("T1", "Query", "d", List.of()),
            new PlannedTask("T2", "Endpoint", "d", List.of("T1"))));

    private static final DesignSpec CYCLE = new DesignSpec("Broken", List.of(
            new PlannedTask("T1", "A", "d", List.of("T2")),
            new PlannedTask("T2", "B", "d", List.of("T1"))));

    private DesignStep step(DesignAgent agent) {
        return new DesignStep(agent, new DesignSpecValidator());
    }

    @Test
    void validPlanCompletesOnFirstAttempt() {
        var outcome = step((r, d, f) -> VALID).run(spec, List.of());

        assertThat(outcome.status()).isEqualTo(StepStatus.COMPLETED);
        assertThat(outcome.attempts()).isEqualTo(1);
        assertThat(outcome.output()).isEqualTo(VALID);
    }

    @Test
    void cycleIsFedBackAndFixedOnSecondAttempt() {
        AtomicReference<List<String>> feedbackSeen = new AtomicReference<>();
        DesignAgent fixesItself = (r, d, feedback) -> {
            if (feedback.isEmpty()) return CYCLE;
            feedbackSeen.set(feedback);
            return VALID;
        };

        var outcome = step(fixesItself).run(spec, List.of());

        assertThat(outcome.status()).isEqualTo(StepStatus.COMPLETED);
        assertThat(outcome.attempts()).isEqualTo(2);
        assertThat(feedbackSeen.get()).singleElement().asString().startsWith("cycle among tasks: T1, T2");
    }

    @Test
    void alwaysInvalidFailsAfterMaxAttempts() {
        var outcome = step((r, d, f) -> CYCLE).run(spec, List.of());

        assertThat(outcome.status()).isEqualTo(StepStatus.FAILED);
        assertThat(outcome.attempts()).isEqualTo(DesignStep.MAX_ATTEMPTS);
        assertThat(outcome.output()).isNull();
        assertThat(outcome.errors()).last().asString().contains("still invalid after 2 attempts");
    }

    @Test
    void agentExceptionFailsWithoutRetry() {
        var outcome = step((r, d, f) -> { throw new IllegalStateException("model down"); }).run(spec, List.of());

        assertThat(outcome.status()).isEqualTo(StepStatus.FAILED);
        assertThat(outcome.attempts()).isEqualTo(1);
        assertThat(outcome.errors()).containsExactly("agent failed: model down");
    }
}

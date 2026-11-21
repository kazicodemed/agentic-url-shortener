package com.schwab.urlshortener.orchestration.agent.design;

import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The stub stands in for the real model, so its plans must pass our own validator. */
class StubDesignAgentTest {

    private final StubDesignAgent agent = new StubDesignAgent();
    private final DesignSpecValidator validator = new DesignSpecValidator();

    private final RequirementSpec spec =
            new RequirementSpec("Make the system more scalable", List.of("Handle peak load"), List.of());

    @Test
    void loadDecisionProducesAValidCachingPlan() {
        List<HumanDecision> decisions = List.of(new HumanDecision(
                "What peak load must the system handle?", "Plan for 5,000 redirects per second"));

        DesignSpec plan = agent.design(spec, decisions, List.of());

        assertThat(validator.validate(plan).isValid()).isTrue();
        assertThat(plan.approach()).contains("Redis");
        assertThat(plan.tasks()).extracting(PlannedTask::title)
                .anyMatch(title -> title.contains("load test"));
    }

    @Test
    void withoutDecisionsTheSimplePlanIsUsed() {
        DesignSpec plan = agent.design(spec, List.of(), List.of());

        assertThat(validator.validate(plan).isValid()).isTrue();
        assertThat(plan.approach()).doesNotContain("Redis");
    }

    @Test
    void nullInputsDoNotCrash() {
        DesignSpec plan = agent.design(spec, null, null);

        assertThat(validator.validate(plan).isValid()).isTrue();
    }

    @Test
    void anUnrelatedDecisionDoesNotTriggerCaching() {
        List<HumanDecision> decisions = List.of(new HumanDecision(
                "Which auth method?", "Use the existing API key"));

        assertThat(agent.design(spec, decisions, List.of()).approach()).doesNotContain("Redis");
    }
}

package com.schwab.urlshortener.orchestration.step;

import com.schwab.urlshortener.orchestration.agent.Impact;
import com.schwab.urlshortener.orchestration.agent.requirements.Ambiguity;
import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;
import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpecValidator;
import com.schwab.urlshortener.orchestration.agent.requirements.RequirementsAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequirementsStepTest {

    @Test
    void invalidThenValidCompletesOnSecondAttempt() {
        RequirementsAgent fixesItself = (req, feedback) -> feedback.isEmpty()
                ? new RequirementSpec(null, List.of("GET /stats"), List.of())          // 1st: intent missing
                : new RequirementSpec("Show clicks", List.of("GET /stats"), List.of()); // 2nd: fixed

        RequirementsStep requirementsStep = new RequirementsStep(fixesItself, new RequirementSpecValidator());

        var outcome = requirementsStep.run("Show clicks");
        assertEquals(StepStatus.COMPLETED, outcome.status());
        assertEquals(2, outcome.attempts());

    }

    @Test
    void validRequirementCompletesImmediately() {
        RequirementsAgent alwaysValid = (req, feedback) -> new RequirementSpec("Show clicks", List.of("GET /stats"), List.of());
        RequirementsStep requirementsStep = new RequirementsStep(alwaysValid, new RequirementSpecValidator());
        var outcome = requirementsStep.run("Show clicks");
        assertEquals(StepStatus.COMPLETED, outcome.status());
        assertEquals(1, outcome.attempts());
    }

    @Test
    void invalidHighLevelRequirementFails() {
        RequirementsAgent alwaysInvalid = (req, feedback) -> new RequirementSpec(null, List.of(), List.of());
        RequirementsStep requirementsStep = new RequirementsStep(alwaysInvalid, new RequirementSpecValidator());
        var outcome = requirementsStep.run("Show clicks");
        assertEquals(StepStatus.FAILED, outcome.status());
        assertEquals(2, outcome.attempts());
    }

    @Test
    void highAmbiguityNeedsHuman() {
        RequirementsAgent alwaysNeedsHuman = (req, feedback) -> new RequirementSpec("Show clicks",
                List.of("GET /stats"), List.of(new Ambiguity("What is the maximum length of the URL?", null, Impact.HIGH)));
        RequirementsStep requirementsStep = new RequirementsStep(alwaysNeedsHuman, new RequirementSpecValidator());
        var outcome = requirementsStep.run("Show clicks");
        assertEquals(StepStatus.NEEDS_HUMAN, outcome.status());
        assertEquals(1, outcome.attempts());
    }

    @Test
    void agentExceptionFailsWithoutRetry() {
        RequirementsAgent throwsException = (req, feedback) -> {
            throw new RuntimeException("agent failed");
        };

        RequirementsStep requirementsStep = new RequirementsStep(throwsException, new RequirementSpecValidator());
        var outcome = requirementsStep.run("Show clicks");
        assertEquals(StepStatus.FAILED, outcome.status());
        assertEquals(1, outcome.attempts());
    }

    @Test
    void invalidSpecWithHighAmbiguityRetriesInsteadOfAskingHuman() {
        // 1st answer is broken (intent missing) even though it has a HIGH ambiguity.
        // The step must retry to fix the shape first, and only then hand the
        // still-HIGH ambiguity to a human. Validity is checked before needsHuman.
        Ambiguity blocking = new Ambiguity("What is the maximum length of the URL?", "2048 characters", Impact.HIGH);
        RequirementsAgent brokenThenValid = (req, feedback) -> feedback.isEmpty()
                ? new RequirementSpec(null, List.of(), List.of(blocking))               // 1st: invalid, intent missing
                : new RequirementSpec("Show clicks", List.of(), List.of(blocking));    // 2nd: valid, still HIGH

        RequirementsStep requirementsStep = new RequirementsStep(brokenThenValid, new RequirementSpecValidator());

        var outcome = requirementsStep.run("Show clicks");
        assertEquals(StepStatus.NEEDS_HUMAN, outcome.status());
        assertEquals(2, outcome.attempts());   // proves it retried before asking a human
    }
}

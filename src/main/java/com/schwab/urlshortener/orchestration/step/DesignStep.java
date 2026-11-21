package com.schwab.urlshortener.orchestration.step;

import com.schwab.urlshortener.orchestration.agent.ValidationResult;
import com.schwab.urlshortener.orchestration.agent.design.DesignAgent;
import com.schwab.urlshortener.orchestration.agent.design.DesignSpec;
import com.schwab.urlshortener.orchestration.agent.design.DesignSpecValidator;
import com.schwab.urlshortener.orchestration.agent.design.HumanDecision;
import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Design stage: same evaluator-optimizer loop as RequirementsStep.
 * The agent proposes a plan, the validator checks it is a runnable DAG,
 * and the validator's errors become feedback for one more attempt.
 * Design never asks a human: a broken plan is the model's to fix.
 */
@Component
@AllArgsConstructor
public class DesignStep {

    static final int MAX_ATTEMPTS = 2;

    private final DesignAgent designAgent;
    private final DesignSpecValidator designSpecValidator;

    public StepOutcome<DesignSpec> run(RequirementSpec requirements, List<HumanDecision> decisions) {
        List<String> feedback = List.of();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            DesignSpec plan;
            try {
                plan = designAgent.design(requirements, decisions, feedback);
            } catch (Exception e) {
                // the model client already retries transport errors; don't stack another retry here
                return new StepOutcome<>(StepStatus.FAILED, null,
                        List.of("agent failed: " + e.getMessage()), attempt);
            }

            ValidationResult result = designSpecValidator.validate(plan);
            if (result.isValid()) {
                return new StepOutcome<>(StepStatus.COMPLETED, plan, List.of(), attempt);
            }
            feedback = result.errors();
        }

        List<String> errors = new ArrayList<>(feedback);
        errors.add("still invalid after " + MAX_ATTEMPTS + " attempts");
        return new StepOutcome<>(StepStatus.FAILED, null, errors, MAX_ATTEMPTS);
    }
}

package com.schwab.urlshortener.orchestration.step;

import com.schwab.urlshortener.orchestration.agent.ValidationResult;
import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;
import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpecValidator;
import com.schwab.urlshortener.orchestration.agent.requirements.RequirementsAgent;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class RequirementsStep {

    static final int MAX_ATTEMPTS = 2;

    private final RequirementsAgent requirementsAgent;
    private final RequirementSpecValidator requirementSpecValidator;

    public StepOutcome<RequirementSpec> run(String rawRequirement) {
        List<String> feedback = List.of();
        RequirementSpec spec;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                spec = requirementsAgent.analyze(rawRequirement, feedback);
            } catch (Exception e) {
                // Spring AI already retried the shape, so don't retry again here
                return new StepOutcome<>(StepStatus.FAILED, null,
                        List.of("agent failed: " + e.getMessage()), attempt);
            }

            ValidationResult validationResult = requirementSpecValidator.validate(spec);

            if (validationResult.isValid() && validationResult.needsHuman()) {
                return new StepOutcome<>(StepStatus.NEEDS_HUMAN, spec, List.of(), attempt);  // stops the loop
            } else if ( validationResult.isValid()) {
                return new StepOutcome<>(StepStatus.COMPLETED, spec, List.of(), attempt);
            } else {
                feedback = validationResult.errors();
            }

        }

        List<String> errors = new ArrayList<>(feedback);
        errors.add("still invalid after " + MAX_ATTEMPTS + " attempts");

        return new StepOutcome<>(StepStatus.FAILED, null, errors, MAX_ATTEMPTS);
    }


}

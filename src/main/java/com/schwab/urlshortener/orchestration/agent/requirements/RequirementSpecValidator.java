package com.schwab.urlshortener.orchestration.agent.requirements;

import com.schwab.urlshortener.orchestration.agent.Impact;
import com.schwab.urlshortener.orchestration.agent.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Business-rule checks on a RequirementSpec returned by the model.
 *
 * <p>null means the model left a field out (an error); an empty list is a real
 * answer ("nothing here") and is valid. Error messages are written for the model,
 * because they are fed back to it on a retry.
 */
@Component
public class RequirementSpecValidator {

    public ValidationResult validate(RequirementSpec spec) {
        List<String> errors = new ArrayList<>();
        boolean needsHuman = false;

        if (spec.intent() == null || spec.intent().isBlank()) {
            errors.add("intent is missing");
        }
        if (spec.functionalRequirements() == null) {
            errors.add("functionalRequirements is missing; use [] if there are none");
        }
        if (spec.ambiguities() == null) {
            errors.add("ambiguities is missing; use [] if there are none");
        } else {
            for (int i = 0; i < spec.ambiguities().size(); i++) {
                Ambiguity ambiguity = spec.ambiguities().get(i);
                if (ambiguity.question() == null || ambiguity.question().isBlank()) {
                    errors.add("ambiguities[" + i + "].question is missing");
                }
                if (ambiguity.impact() == null) {
                    errors.add("ambiguities[" + i + "].impact is missing");
                } else if (ambiguity.impact() == Impact.HIGH) {
                    needsHuman = true;
                }
            }
        }

        if (spec.functionalRequirements() != null && spec.functionalRequirements().size() > 10) {
            errors.add("too many functional requirements; limit to 10");
        }

        if(spec.functionalRequirements() != null && spec.functionalRequirements().isEmpty() &&
                !needsHuman) {
            errors.add("no requirements: if the request is unclear, mark the blocking question as HIGH");
        }

        return new ValidationResult(errors, needsHuman);
    }
}

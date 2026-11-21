package com.schwab.urlshortener.orchestration.agent.requirements;

import com.schwab.urlshortener.orchestration.agent.Impact;
import com.schwab.urlshortener.orchestration.agent.ValidationResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class RequirementSpecValidatorTest {

    private final RequirementSpecValidator validator = new RequirementSpecValidator();

    @Test
    void clearSpecIsValidAndNeedsNoHumanReview() {
        RequirementSpec spec = new RequirementSpec(
                "I want to shorten URLs",
                List.of("Shorten URLs"),
                List.of()
        );
        ValidationResult result = validator.validate(spec);
        assertTrue(result.isValid());
        assertFalse(result.needsHuman());
    }

    @Test
    void highAmbiguityIsValidButNeedsHumanReview() {
        RequirementSpec spec = new RequirementSpec(
                "I want to shorten URLs",
                List.of("Shorten URLs"),
                List.of(new Ambiguity("What is the maximum length of the URL?", null, Impact.HIGH))
        );
        ValidationResult result = validator.validate(spec);
        assertTrue(result.isValid());
        assertTrue(result.needsHuman());
    }

    @Test
    void nullAmbiguitiesIsInvalid() {
        RequirementSpec spec = new RequirementSpec(
                "I want to shorten URLs",
                List.of("Shorten URLs"),
                null
        );
        ValidationResult result = validator.validate(spec);
        assertFalse(result.isValid());
    }

    @Test
    void noRequirementsWithoutHighAmbiguityIsInvalid() {
        RequirementSpec spec = new RequirementSpec(
                "I want to shorten URLs",
                List.of(),
                List.of(new Ambiguity("What is the maximum length of the URL?", null, Impact.LOW))
        );
        ValidationResult result = validator.validate(spec);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("no requirements: if the request is unclear, mark the blocking question as HIGH")));
    }

    @Test
    void moreThanTenRequirementsIsInvalid() {
        RequirementSpec spec = new RequirementSpec(
                "I want to shorten URLs",
                List.of("Req1", "Req2", "Req3", "Req4", "Req5", "Req6", "Req7", "Req8", "Req9", "Req10", "Req11"),
                List.of()
        );
        ValidationResult result = validator.validate(spec);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("too many functional requirements; limit to 10")));
    }

    @Test
    void noRequirementsWithHighAmbiguityNeedsHuman() {
        RequirementSpec spec = new RequirementSpec(
                "Hello world",
                List.of(),
                List.of(new Ambiguity("What should be built?", "Nothing until clarified", Impact.HIGH)));
        ValidationResult result = validator.validate(spec);
        assertTrue(result.isValid());
        assertTrue(result.needsHuman());
    }
}
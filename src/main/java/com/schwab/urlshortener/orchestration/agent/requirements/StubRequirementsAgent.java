package com.schwab.urlshortener.orchestration.agent.requirements;

import com.schwab.urlshortener.orchestration.agent.Impact;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Free, deterministic default. Replaced by ClaudeRequirementsAgent when orchestration.agents.mode=claude. */
@Component
@ConditionalOnProperty(name = "orchestration.agents.mode", havingValue = "stub", matchIfMissing = true)
public class StubRequirementsAgent implements RequirementsAgent {
    @Override
    public RequirementSpec analyze(String rawRequirement, List<String> feedback) {
        if (rawRequirement == null) {
            rawRequirement = "";
        }
        String lower = rawRequirement.toLowerCase();

        // Scenario: greenfield — a new feature, clear enough to run without a human.
        if (lower.contains("total click")) {
            return greenfieldTotalClicks();
        }
        // Scenario: brownfield — a real bug in this codebase; the fix changes the schema,
        // a high-impact action, so it needs change-control approval.
        if (lower.contains("custom") && (lower.contains("fix") || lower.contains("bug") || lower.contains("fail"))) {
            return brownfieldCustomCodeLength();
        }
        // Scenario: ambiguous — a vague request, so the run must stop for a human.
        if (lower.contains("scalable")) {
            return checkRequirementAndAmbiguity(rawRequirement);
        }
        if(rawRequirement.contains("checkRequirementAndAmbiguity")) {
            return checkRequirementAndAmbiguity(rawRequirement);
        } else if(rawRequirement.contains("checkRequirementAndNoAmbiguity")) {
            return checkRequirementAndNoAmbiguity(rawRequirement);
        } else if(rawRequirement.contains("emptyRequirementAndNoAmbiguity")) {
            return emptyRequirementAndNoAmbiguity(rawRequirement);
        } else if(rawRequirement.contains("emptyRequirementAndHasAmbiguity")) {
            return emptyRequirementAndHasAmbiguity(rawRequirement);
        } else if(rawRequirement.contains("emptyRequirementAndHasHighAmbiguity")) {
            return emptyRequirementAndHasHighAmbiguity(rawRequirement);
        }
        return new RequirementSpec(
            "stub intent",
            List.of("stub functional requirement"),
            List.of(new Ambiguity("stub question", "stub proposed default", Impact.LOW))
        );
    }

    private RequirementSpec greenfieldTotalClicks() {
        return new RequirementSpec(
                "Expose the total number of clicks for a short code through the API",
                List.of(
                        "GET /api/v1/analytics/{shortCode}/total returns the total click count",
                        "Unknown short codes return 404, not 500",
                        "The endpoint is documented and covered by tests"),
                List.of(new Ambiguity(
                        "Should clicks on expired links be counted?",
                        "Yes: the total is historical",
                        Impact.LOW)));
    }

    private RequirementSpec brownfieldCustomCodeLength() {
        return new RequirementSpec(
                "Fix custom short codes of 11 to 20 characters failing with a server error",
                List.of(
                        "Custom codes of 3 to 20 characters are accepted and stored",
                        "Codes outside 3 to 20 characters are rejected with 400, never 500",
                        "Existing short codes and redirects keep working"),
                List.of(
                        new Ambiguity(
                                "The fix changes the url_mappings.short_code column. Approve this schema change?",
                                "Widen the column from 10 to 20 characters (backward compatible)",
                                Impact.HIGH),
                        new Ambiguity(
                                "Should the limit be 10 (tighten validation) or 20 (widen the column)?",
                                "20: validation already allows it and clients may rely on it",
                                Impact.MEDIUM)));
    }

    private RequirementSpec checkRequirementAndAmbiguity(String message) {
        return new RequirementSpec(
                "Improve the URL shortener's ability to handle more traffic",
                List.of(
                        "Cache redirect lookups so most requests skip the database",
                        "Keep p99 redirect latency under 50 ms at peak load"),
                List.of(
                        new Ambiguity(
                                "What peak load must the system handle?",
                                "1,000 redirects per second",
                                Impact.HIGH),
                        new Ambiguity(
                                "How long may cached redirects be stale?",
                                "60 seconds",
                                Impact.LOW)));
    }


    private RequirementSpec checkRequirementAndNoAmbiguity(String message) {
        return new RequirementSpec(
                "Improve the URL shortener's ability to handle more traffic",
                List.of(
                        "Cache redirect lookups so most requests skip the database",
                        "Keep p99 redirect latency under 50 ms at peak load"),
                List.of());
    }


    private RequirementSpec emptyRequirementAndNoAmbiguity(String message) {
        return new RequirementSpec(
                "Improve the URL shortener's ability to handle more traffic",
                List.of(),
                List.of());
    }


    private RequirementSpec emptyRequirementAndHasAmbiguity(String message) {
        return new RequirementSpec(
                "Improve the URL shortener's ability to handle more traffic",
                List.of(),
                List.of(
                        new Ambiguity(
                                "What peak load must the system handle?",
                                "1,000 redirects per second",
                                Impact.MEDIUM),
                        new Ambiguity(
                                "How long may cached redirects be stale?",
                                "60 seconds",
                                Impact.LOW)));
    }


    private RequirementSpec emptyRequirementAndHasHighAmbiguity(String message) {
        return new RequirementSpec(
                "Improve the URL shortener's ability to handle more traffic",
                List.of(),
                List.of(
                        new Ambiguity(
                                "What peak load must the system handle?",
                                "1,000 redirects per second",
                                Impact.HIGH),
                        new Ambiguity(
                                "How long may cached redirects be stale?",
                                "60 seconds",
                                Impact.LOW)));
    }

}

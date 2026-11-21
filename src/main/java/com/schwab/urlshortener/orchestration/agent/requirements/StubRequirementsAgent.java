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
        // Demo scenario from the brief: a vague request, so the run must stop for a human.
        if (rawRequirement.toLowerCase().contains("scalable")) {
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

package com.schwab.urlshortener.orchestration.agent.design;

import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Free, deterministic default. Replaced by ClaudeDesignAgent when orchestration.agents.mode=claude. */
@Component
@ConditionalOnProperty(name = "orchestration.agents.mode", havingValue = "stub", matchIfMissing = true)
public class StubDesignAgent implements DesignAgent {
    @Override
    public DesignSpec design(RequirementSpec requirements, List<HumanDecision> decisions, List<String> feedback) {
        boolean highLoad = decisions != null && decisions.stream()
                .anyMatch(d -> d.answer() != null 
                        && d.answer().toLowerCase().matches(".*(/sec|per second|redirects).*"));
        if (highLoad) {
            return cachingPlan();
        }
        String intent = requirements == null || requirements.intent() == null
                ? "" : requirements.intent().toLowerCase();
        if (intent.contains("custom short codes")) {
            return brownfieldCustomCodePlan();
        }
        if (intent.contains("total number of clicks")) {
            return greenfieldTotalClicksPlan();
        }
        return simplePlan();
    }


    private DesignSpec cachingPlan() {
        return new DesignSpec(
                "Cache short-code lookups in Redis to serve 5,000 redirects/sec",
                List.of(
                        new PlannedTask("T1", "Add Redis cache for short-code lookups",
                                "Cache UrlMapping by short code with a TTL", List.of()),
                        new PlannedTask("T2", "Invalidate cache on URL update or expiry",
                                "Evict the entry when a mapping changes or expires", List.of("T1")),
                        new PlannedTask("T3", "Add load test for 5,000 redirects/sec",
                                "Measure p99 latency under target load", List.of("T1")),
                        new PlannedTask("T4", "Document capacity and cache TTL decision",
                                "Record the chosen TTL and measured capacity", List.of("T2", "T3"))));
    }

    /** Greenfield: T1 and T3 are independent, so they run in parallel in the first wave. */
    private DesignSpec greenfieldTotalClicksPlan() {
        return new DesignSpec(
                "Add a count query on AnalyticsEventRepository and a GET endpoint on UrlShortenerController; "
                        + "no schema change, no new infrastructure",
                List.of(
                        new PlannedTask("T1", "Add click-count query to AnalyticsEventRepository",
                                "countByShortCode(String shortCode)", List.of()),
                        new PlannedTask("T2", "Add GET /api/v1/analytics/{shortCode}/total",
                                "Service method + controller endpoint; 404 via UrlNotFoundException", List.of("T1")),
                        new PlannedTask("T3", "Document the endpoint",
                                "README API table and example response", List.of()),
                        new PlannedTask("T4", "Add unit and integration tests",
                                "Known code, unknown code (404), zero clicks", List.of("T2"))));
    }

    /**
     * Brownfield: the approach records the codebase reasoning — which modules are impacted
     * and how the request flows through them — before any task is planned.
     */
    private DesignSpec brownfieldCustomCodePlan() {
        return new DesignSpec(
                "Impacted: UrlMapping.shortCode (column length 10), UrlShortenerService.validateCustomCode "
                        + "(allows 20), ShortenUrlRequest. Flow: POST /api/v1/shorten -> UrlShortenerController "
                        + "-> UrlShortenerService.validateCustomCode -> saveWithRetry -> url_mappings. "
                        + "Root cause: validation and schema disagree. Fix: one limit (20) used by both.",
                List.of(
                        new PlannedTask("T1", "Reproduce with a failing test",
                                "A 15-character custom code passes validation, then fails on insert", List.of()),
                        new PlannedTask("T2", "Widen UrlMapping.shortCode to 20 characters",
                                "@Column(length = 20); approved, backward-compatible schema change", List.of("T1")),
                        new PlannedTask("T3", "Use one constant for the code length limit",
                                "Validation and column length read the same constant so they cannot drift",
                                List.of("T1")),
                        new PlannedTask("T4", "Add regression tests and a changelog note",
                                "3, 10, 11, 20 and 21-character codes; note the schema change", List.of("T2", "T3"))));
    }

    private DesignSpec simplePlan() {
        return new DesignSpec(
                "Implement the change directly on the existing service; no new infrastructure needed",
                List.of(
                        new PlannedTask("T1", "Implement the change in the service layer",
                                "Add the logic to the existing service and repository", List.of()),
                        new PlannedTask("T2", "Expose it through the REST API",
                                "Add or update the controller endpoint and request validation", List.of("T1")),
                        new PlannedTask("T3", "Add unit and integration tests",
                                "Cover the new behavior and the error cases", List.of("T1", "T2"))));
    }
}

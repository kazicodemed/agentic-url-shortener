package com.schwab.urlshortener.orchestration.agent.design;

import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StubDesignAgent implements DesignAgent {
    @Override
    public DesignSpec design(RequirementSpec requirements, List<HumanDecision> decisions, List<String> feedback) {
        boolean highLoad = decisions != null && decisions.stream()
                .anyMatch(d -> d.answer() != null 
                        && d.answer().toLowerCase().matches(".*(/sec|per second|redirects).*"));
        return highLoad ? cachingPlan() : simplePlan();
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

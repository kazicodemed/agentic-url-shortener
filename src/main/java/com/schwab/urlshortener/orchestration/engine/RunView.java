package com.schwab.urlshortener.orchestration.engine;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Read model for a run: what the API returns. Entities never leave the service layer. */
public record RunView(
        UUID id,
        String rawRequirement,
        String status,
        LocalDateTime createdAt,
        List<TaskView> tasks,
        List<TransitionView> auditTrail,
        List<DecisionView> decisions) {

    public record TaskView(UUID id, String taskType, String status, Integer attempts,
                           JsonNode output, String errors) {}

    public record TransitionView(LocalDateTime occurredAt, String scope,
                                 String fromStatus, String toStatus, String reason) {}

    public record DecisionView(String question, String decision, String decidedBy, LocalDateTime decidedAt){}
}

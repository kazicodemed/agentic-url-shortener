package com.schwab.urlshortener.orchestration.api;

import com.schwab.urlshortener.orchestration.engine.OrchestrationEngine;
import com.schwab.urlshortener.orchestration.engine.ReliabilityMetrics;
import com.schwab.urlshortener.orchestration.engine.ReliabilityMetricsService;
import com.schwab.urlshortener.orchestration.engine.RunView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * POST /api/v1/runs          start a run for a requirement
 * GET  /api/v1/runs/{id}     the run, its tasks and its audit trail
 * POST /api/v1/runs/{id}/approve  a person answers the open question; the run resumes
 * POST /api/v1/runs/{id}/reject   reject the plan with a comment; design re-plans
 * GET  /api/v1/runs/metrics  reliability metrics across all runs
 */
@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunController {

    private final OrchestrationEngine engine;
    private final ReliabilityMetricsService metricsService;

    /** reviewPlan (optional, default false): a person must approve the plan before execution. */
    public record StartRunRequest(@NotBlank String requirement, Boolean reviewPlan) {}

    @PostMapping
    public ResponseEntity<RunView> start(@Valid @RequestBody StartRunRequest request) {
        UUID id = engine.startRun(request.requirement(), Boolean.TRUE.equals(request.reviewPlan())).getId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/runs/" + id))
                .body(engine.getRun(id));
    }

    @GetMapping("/metrics")
    public ReliabilityMetrics metrics() {
        return metricsService.compute();
    }

    @GetMapping("/{id}")
    public RunView get(@PathVariable UUID id) {
        return engine.getRun(id);
    }

    public record RejectRequest(@NotBlank String rejectedBy, @NotBlank String comment) {}

    @PostMapping("/{id}/reject")
    public RunView reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        engine.rejectPlan(id, request.rejectedBy(), request.comment());
        return engine.getRun(id);
    }

    public record ApproveRequest(@NotBlank String approvedBy, @NotBlank String decision) {}

    @PostMapping("/{id}/approve")
    public RunView approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest request) {
        engine.approve(id, request.approvedBy(), request.decision());
        return engine.getRun(id);
    }
}

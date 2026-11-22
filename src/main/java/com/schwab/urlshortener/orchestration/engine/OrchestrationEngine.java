package com.schwab.urlshortener.orchestration.engine;

import com.schwab.urlshortener.orchestration.agent.Impact;
import com.schwab.urlshortener.orchestration.agent.design.DesignSpec;
import com.schwab.urlshortener.orchestration.agent.design.HumanDecision;
import com.schwab.urlshortener.orchestration.agent.requirements.Ambiguity;
import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;
import com.schwab.urlshortener.orchestration.execution.ExecutionOutcome;
import com.schwab.urlshortener.orchestration.execution.ExecutionStep;
import com.schwab.urlshortener.orchestration.execution.TaskResult;
import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import com.schwab.urlshortener.orchestration.run.*;
import com.schwab.urlshortener.orchestration.step.DesignStep;
import com.schwab.urlshortener.orchestration.step.RequirementsStep;
import com.schwab.urlshortener.orchestration.step.StepOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Coordinates a run through its stages: REQUIREMENTS → (human, if needed) → DESIGN → EXECUTE.
 *
 * <p>Rule: write the state first, then act. Each task is saved as RUNNING before
 * its agent is called, so an interrupted call leaves evidence behind.
 *
 * <p>Human decisions are stored as {@link DecisionRecord}s and handed to the next
 * stage as fixed constraints, so an answer like "5,000 redirects/sec" changes the plan.
 *
 * <p>Known simplification: each public method holds one DB transaction across the
 * agent calls. With the stub that is instant; with a real LLM call (seconds) the work
 * should be split so no DB connection is held while waiting on the model.
 */
@Service
@RequiredArgsConstructor
public class OrchestrationEngine {

    static final String REQUIREMENTS = "REQUIREMENTS";
    static final String DESIGN = "DESIGN";
    static final String EXECUTE_PREFIX = "EXECUTE ";

    private final OrchestrationRunRepository runRepo;
    private final TaskExecutionRepository taskRepo;
    private final StateTransitionRepository transitionRepo;
    private final DecisionRecordRepository decisionRecordRepository;
    private final RequirementsStep requirementsStep;
    private final DesignStep designStep;
    private final ExecutionStep executionStep;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // ---------------------------------------------------------------- public API

    @Transactional
    public OrchestrationRun startRun(String rawRequirement) {
        // 1. the run exists before any work happens (status RUNNING)
        OrchestrationRun run = runRepo.save(OrchestrationRun.builder()
                .rawRequirement(rawRequirement)
                .status(RunStatus.RUNNING)
                .build());
        record(run, null, null, RunStatus.RUNNING.name(), "run created");

        // 2. requirements stage
        TaskExecution task = startTask(run, REQUIREMENTS, "requirements analysis started");
        StepOutcome<RequirementSpec> outcome = timed(REQUIREMENTS, () -> requirementsStep.run(rawRequirement));
        storeOutcome(task, outcome);

        switch (outcome.status()) {
            case COMPLETED -> {
                changeTaskStatus(run, task, TaskStatus.COMPLETED, "requirements spec validated");
                runDesign(run, outcome.output(), List.of());   // no human needed: go straight on
            }
            case NEEDS_HUMAN -> {
                changeTaskStatus(run, task, TaskStatus.AWAITING_HUMAN,
                        "HIGH impact ambiguity: " + firstHighQuestion(outcome.output()));
                changeRunStatus(run, RunStatus.AWAITING_HUMAN, "waiting for a person to resolve the ambiguity");
            }
            default -> fail(run, task, "requirements failed: " + String.join("; ", outcome.errors()));
        }
        return run;
    }

    /** A person answers the open question; the run resumes and continues to design. */
    @Transactional
    public OrchestrationRun approve(UUID runId, String approvedBy, String decision) {
        OrchestrationRun run = runRepo.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No run " + runId));

        // guard: only a waiting run can be approved (a second approve gets 409)
        if (run.getStatus() != RunStatus.AWAITING_HUMAN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Run is " + run.getStatus() + ", not awaiting a human");
        }

        TaskExecution task = taskRepo.findByRunId(runId).stream()
                .filter(t -> t.getStatus() == TaskStatus.AWAITING_HUMAN)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Run " + runId + " is AWAITING_HUMAN but has no task awaiting a human"));

        RequirementSpec spec = objectMapper.readValue(task.getOutputJson(), RequirementSpec.class);

        // the answer is stored as data (lineage), not only as text in the audit trail
        decisionRecordRepository.save(DecisionRecord.builder()
                .run(run).task(task)
                .question(firstHighQuestion(spec))
                .decision(decision)
                .decidedBy(approvedBy)
                .build());

        changeTaskStatus(run, task, TaskStatus.COMPLETED, "approved by " + approvedBy + ": " + decision);
        changeRunStatus(run, RunStatus.RUNNING, "resumed after human approval");

        runDesign(run, spec, decisionsFor(runId));
        return run;
    }

    /** The run with its tasks, audit trail and decisions, read inside a transaction (LAZY links are safe here). */
    @Transactional(readOnly = true)
    public RunView getRun(UUID runId) {
        OrchestrationRun run = runRepo.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No run " + runId));

        List<RunView.TaskView> tasks = taskRepo.findByRunId(runId).stream()
                .map(t -> new RunView.TaskView(
                        t.getId(), t.getTaskType(), t.getStatus().name(), t.getAttempts(),
                        t.getOutputJson() == null ? null : objectMapper.readTree(t.getOutputJson()),
                        t.getErrors()))
                .toList();

        List<RunView.TransitionView> trail = transitionRepo.findByRunIdOrderByOccurredAtAsc(runId).stream()
                .map(st -> new RunView.TransitionView(
                        st.getOccurredAt(),
                        st.getTask() == null ? "RUN" : st.getTask().getTaskType(),
                        st.getFromStatus(), st.getToStatus(), st.getReason()))
                .toList();

        List<RunView.DecisionView> decisions = decisionRecordRepository
                .findByRunIdOrderByDecidedAtAsc(runId).stream()
                .map(d -> new RunView.DecisionView(
                        d.getQuestion(), d.getDecision(), d.getDecidedBy(), d.getDecidedAt()))
                .toList();

        return new RunView(run.getId(), run.getRawRequirement(), run.getStatus().name(),
                run.getCreatedAt(), tasks, trail, decisions);
    }

    // ---------------------------------------------------------------- stages

    /** Design stage: the plan is built from the requirements plus every human decision so far. */
    private void runDesign(OrchestrationRun run, RequirementSpec spec, List<HumanDecision> decisions) {
        TaskExecution task = startTask(run, DESIGN, "design started with " + decisions.size() + " human decision(s)");
        StepOutcome<DesignSpec> outcome = timed(DESIGN, () -> designStep.run(spec, decisions));
        storeOutcome(task, outcome);

        switch (outcome.status()) {
            case COMPLETED -> {
                changeTaskStatus(run, task, TaskStatus.COMPLETED,
                        "plan validated: " + outcome.output().tasks().size() + " tasks");
                runExecution(run, outcome.output());
            }
            default -> fail(run, task, "design failed: " + String.join("; ", outcome.errors()));
        }
    }

    /**
     * Execution stage: one task row per planned task, then {@link ExecutionStep} runs the plan
     * in waves (parallel within a wave, on virtual threads). Worker threads never touch JPA:
     * every state change below is recorded here, on the engine thread, after the waves return.
     *
     * <p>On failure the run stops safely: tasks that never started are SKIPPED, and tasks that
     * had completed are ROLLED_BACK, so the run never ends half-applied.
     */
    private void runExecution(OrchestrationRun run, DesignSpec plan) {
        // write state first: every planned task exists as PENDING before any work starts
        Map<String, TaskExecution> rows = new LinkedHashMap<>();
        for (PlannedTask planned : plan.tasks()) {
            rows.put(planned.id(), taskRepo.save(TaskExecution.builder()
                    .run(run)
                    .taskType(EXECUTE_PREFIX + planned.id())
                    .status(TaskStatus.PENDING)
                    .build()));
        }

        ExecutionOutcome outcome = timed("EXECUTE", () -> executionStep.run(plan.tasks()));

        for (TaskResult result : outcome.results()) {
            TaskExecution row = rows.get(result.taskId());
            row.setAttempts(1);
            row.setOutputJson(objectMapper.writeValueAsString(result));
            if (result.success()) {
                changeTaskStatus(run, row, TaskStatus.COMPLETED, "executed: " + plannedTitle(plan, result.taskId()));
            } else {
                row.setErrors(result.error());
                changeTaskStatus(run, row, TaskStatus.FAILED, "execution failed: " + result.error());
            }
        }

        if (outcome.succeeded()) {
            changeRunStatus(run, RunStatus.COMPLETED, "all stages completed");
            return;
        }

        String failed = outcome.results().stream().filter(r -> !r.success()).map(TaskResult::taskId).toList().toString();
        for (String skippedId : outcome.skippedTaskIds()) {
            changeTaskStatus(run, rows.get(skippedId), TaskStatus.SKIPPED, "not started: " + failed + " failed");
        }
        meterRegistry.counter("orchestration.execution.rollbacks").increment();
        for (TaskResult result : outcome.results()) {
            if (result.success()) {
                changeTaskStatus(run, rows.get(result.taskId()), TaskStatus.ROLLED_BACK,
                        "rolled back: " + failed + " failed in the same run");
            }
        }
        changeRunStatus(run, RunStatus.FAILED,
                "safe-stop: " + failed + " failed; skipped " + outcome.skippedTaskIds() + "; completed tasks rolled back");
    }

    private static String plannedTitle(DesignSpec plan, String taskId) {
        return plan.tasks().stream().filter(t -> t.id().equals(taskId))
                .map(PlannedTask::title).findFirst().orElse(taskId);
    }

    // ---------------------------------------------------------------- helpers

    /** Write state first: the task is PENDING, then RUNNING, before its agent is called. */
    private TaskExecution startTask(OrchestrationRun run, String taskType, String reason) {
        TaskExecution task = taskRepo.save(TaskExecution.builder()
                .run(run)
                .taskType(taskType)
                .status(TaskStatus.PENDING)
                .build());
        changeTaskStatus(run, task, TaskStatus.RUNNING, reason);
        return task;
    }

    /** Stage latency per stage (p50/p95/max in Prometheus): orchestration_stage_duration_seconds. */
    private <T> T timed(String stage, Supplier<T> work) {
        return Timer.builder("orchestration.stage.duration")
                .tag("stage", stage)
                .register(meterRegistry)
                .record(work);
    }

    private void storeOutcome(TaskExecution task, StepOutcome<?> outcome) {
        task.setAttempts(outcome.attempts());
        if (outcome.attempts() > 1) {
            meterRegistry.counter("orchestration.stage.retries", "stage", task.getTaskType()).increment();
        }
        if (outcome.output() != null) {
            task.setOutputJson(objectMapper.writeValueAsString(outcome.output()));
        }
        if (!outcome.errors().isEmpty()) {
            task.setErrors(String.join("\n", outcome.errors()));
        }
    }

    private void fail(OrchestrationRun run, TaskExecution task, String reason) {
        changeTaskStatus(run, task, TaskStatus.FAILED, reason);
        changeRunStatus(run, RunStatus.FAILED, "stopped: a stage failed");
    }

    /** Decision records → the agent-facing type (agents must not depend on the engine or JPA). */
    private List<HumanDecision> decisionsFor(UUID runId) {
        return decisionRecordRepository.findByRunIdOrderByDecidedAtAsc(runId).stream()
                .map(d -> new HumanDecision(d.getQuestion(), d.getDecision()))
                .toList();
    }

    private void changeTaskStatus(OrchestrationRun run, TaskExecution task, TaskStatus to, String reason) {
        String from = task.getStatus() == null ? null : task.getStatus().name();
        task.setStatus(to);
        taskRepo.save(task);
        record(run, task, from, to.name(), reason);
    }

    private void changeRunStatus(OrchestrationRun run, RunStatus to, String reason) {
        String from = run.getStatus() == null ? null : run.getStatus().name();
        if (to != RunStatus.RUNNING) {
            // completed / failed / awaiting_human → success rate and human-gate rate in Prometheus
            meterRegistry.counter("orchestration.runs", "outcome", to.name().toLowerCase()).increment();
        }
        run.setStatus(to);
        runRepo.save(run);
        record(run, null, from, to.name(), reason);
    }

    private void record(OrchestrationRun run, TaskExecution task, String from, String to, String reason) {
        transitionRepo.save(StateTransition.builder()
                .run(run).task(task)
                .fromStatus(from).toStatus(to)
                .reason(reason)
                .build());
    }

    private static String firstHighQuestion(RequirementSpec spec) {
        return spec.ambiguities().stream()
                .filter(a -> a.impact() == Impact.HIGH)
                .map(Ambiguity::question)
                .findFirst()
                .orElse("(unspecified)");
    }
}

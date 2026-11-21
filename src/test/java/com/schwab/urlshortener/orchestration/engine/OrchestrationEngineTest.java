package com.schwab.urlshortener.orchestration.engine;

import com.schwab.urlshortener.orchestration.run.OrchestrationRun;
import com.schwab.urlshortener.orchestration.run.RunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End to end through the engine with the free stub agent: run, task, outcome and
 * audit trail all land in the database. No API key, no cost.
 */
@SpringBootTest
@Transactional
class OrchestrationEngineTest {

    @Autowired
    private OrchestrationEngine engine;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void vagueRequirementStopsForHumanWithFullAuditTrail() {
        OrchestrationRun run = engine.startRun("Make the system more scalable");

        assertThat(run.getStatus()).isEqualTo(RunStatus.AWAITING_HUMAN);

        RunView view = engine.getRun(run.getId());
        assertThat(view.tasks()).hasSize(1);
        RunView.TaskView task = view.tasks().get(0);
        assertThat(task.taskType()).isEqualTo("REQUIREMENTS");
        assertThat(task.status()).isEqualTo("AWAITING_HUMAN");
        assertThat(task.attempts()).isEqualTo(1);
        assertThat(task.output()).isNotNull();                 // the spec is stored for the human and the next stage

        // Rows are written microseconds apart, so compare as a set rather than rely on timestamp order.
        assertThat(view.auditTrail())
                .extracting(t -> t.scope() + ": " + t.fromStatus() + " -> " + t.toStatus())
                .containsExactlyInAnyOrder(
                        "RUN: null -> RUNNING",
                        "REQUIREMENTS: PENDING -> RUNNING",
                        "REQUIREMENTS: RUNNING -> AWAITING_HUMAN",
                        "RUN: RUNNING -> AWAITING_HUMAN");
        assertThat(view.auditTrail())
                .anySatisfy(t -> assertThat(t.reason()).contains("What peak load must the system handle?"));
    }

    @Test
    void clearRequirementRunsRequirementsThenDesign() {
        OrchestrationRun run = engine.startRun("Add an endpoint that returns total clicks");

        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        RunView view = engine.getRun(run.getId());
        assertThat(view.tasks())
                .extracting(t -> t.taskType() + ":" + t.status())
                .containsExactlyInAnyOrder("REQUIREMENTS:COMPLETED", "DESIGN:COMPLETED");
        assertThat(view.auditTrail())
                .extracting(t -> t.scope() + ": " + t.fromStatus() + " -> " + t.toStatus())
                .containsExactlyInAnyOrder(
                        "RUN: null -> RUNNING",
                        "REQUIREMENTS: PENDING -> RUNNING",
                        "REQUIREMENTS: RUNNING -> COMPLETED",
                        "DESIGN: PENDING -> RUNNING",
                        "DESIGN: RUNNING -> COMPLETED",
                        "RUN: RUNNING -> COMPLETED");
        // no human answered anything, so the stub plans the simple change
        assertThat(designOf(view).output().get("approach").asString()).doesNotContain("Redis");
    }

    @Test
    void humanApprovalResumesTheRunIntoDesign() {
        OrchestrationRun run = engine.startRun("Make the system more scalable");
        engine.approve(run.getId(), "kazi", "Plan for 5,000 redirects per second");

        RunView view = engine.getRun(run.getId());
        assertThat(view.status()).isEqualTo("COMPLETED");
        assertThat(view.auditTrail())
                .extracting(t -> t.scope() + ": " + t.fromStatus() + " -> " + t.toStatus())
                .containsExactlyInAnyOrder(
                        "RUN: null -> RUNNING",
                        "REQUIREMENTS: PENDING -> RUNNING",
                        "REQUIREMENTS: RUNNING -> AWAITING_HUMAN",
                        "RUN: RUNNING -> AWAITING_HUMAN",
                        "REQUIREMENTS: AWAITING_HUMAN -> COMPLETED",
                        "RUN: AWAITING_HUMAN -> RUNNING",
                        "DESIGN: PENDING -> RUNNING",
                        "DESIGN: RUNNING -> COMPLETED",
                        "RUN: RUNNING -> COMPLETED");
        assertThat(view.auditTrail())
                .anySatisfy(t -> assertThat(t.reason()).contains("approved by kazi"));
    }

    @Test
    void humanDecisionChangesTheDesignPlan() {
        // the demo moment: the person's answer flows into the next agent and changes its plan
        OrchestrationRun run = engine.startRun("Make the system more scalable");
        engine.approve(run.getId(), "kazi", "Plan for 5,000 redirects per second");

        RunView.TaskView design = designOf(engine.getRun(run.getId()));
        assertThat(design.status()).isEqualTo("COMPLETED");
        assertThat(design.output().get("approach").asString()).contains("Redis");
        assertThat(design.output().get("tasks").toString()).contains("load test");
    }

    @Test
    void approvingTwiceIsAConflict() {
        OrchestrationRun run = engine.startRun("Make the system more scalable");
        engine.approve(run.getId(), "kazi", "Plan for 5,000 redirects per second");

        assertThatThrownBy(() -> engine.approve(run.getId(), "kazi", "again"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test
    void approvingARunThatIsNotWaitingIsAConflict() {
        OrchestrationRun orchestrationRun = engine.startRun("Add an endpoint that returns total clicks");

        assertThatThrownBy(
                () -> {
                    engine.approve(orchestrationRun.getId(), " TestKazi", "updated choice");

                }
        ).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test
    void approvalIsStoredAsADecisionRecord() {
        OrchestrationRun run = engine.startRun("Make the system more scalable");
        engine.approve(run.getId(), "kazi", "Plan for 5,000 redirects per second");

        List<RunView.DecisionView> decisions = engine.getRun(run.getId()).decisions();

        assertThat(decisions).hasSize(1);
        RunView.DecisionView decision = decisions.get(0);
        assertThat(decision.question()).isEqualTo("What peak load must the system handle?");
        assertThat(decision.decision()).isEqualTo("Plan for 5,000 redirects per second");
        assertThat(decision.decidedBy()).isEqualTo("kazi");
        assertThat(decision.decidedAt()).isNotNull();
    }

    @Test
    void runCompletedWithoutHumanHasNoDecisions() {
        OrchestrationRun run = engine.startRun("Add an endpoint that returns total clicks");

        assertThat(engine.getRun(run.getId()).decisions()).isEmpty();
    }

    private static RunView.TaskView designOf(RunView view) {
        return view.tasks().stream().filter(t -> t.taskType().equals("DESIGN")).findFirst().orElseThrow();
    }
}

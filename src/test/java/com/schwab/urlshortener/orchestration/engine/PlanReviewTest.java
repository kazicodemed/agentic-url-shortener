package com.schwab.urlshortener.orchestration.engine;

import com.schwab.urlshortener.orchestration.run.OrchestrationRun;
import com.schwab.urlshortener.orchestration.run.RunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plan review gate (opt-in per run) and re-planning:
 * design → person approves (execute) or rejects with a comment (design re-plans with it).
 */
@SpringBootTest
@Transactional
class PlanReviewTest {

    private static final String GREENFIELD = "Add an endpoint that returns total clicks for a short code";

    @Autowired
    private OrchestrationEngine engine;

    @Test
    void planWaitsForReviewAndNothingIsExecutedUntilApproved() {
        OrchestrationRun run = engine.startRun(GREENFIELD, true);

        RunView waiting = engine.getRun(run.getId());
        assertThat(waiting.status()).isEqualTo(RunStatus.AWAITING_HUMAN.name());
        assertThat(statuses(waiting, "DESIGN")).containsExactly("AWAITING_HUMAN");
        assertThat(statuses(waiting, "EXECUTE")).isEmpty();                        // gate: nothing ran yet

        engine.approve(run.getId(), "kazi", "looks good");

        RunView done = engine.getRun(run.getId());
        assertThat(done.status()).isEqualTo(RunStatus.COMPLETED.name());
        assertThat(statuses(done, "EXECUTE")).hasSize(4).allMatch("COMPLETED"::equals);
        assertThat(done.decisions()).singleElement().satisfies(d -> {
            assertThat(d.question()).isEqualTo(OrchestrationEngine.PLAN_REVIEW_QUESTION);
            assertThat(d.decision()).isEqualTo("Approved: looks good");
        });
    }

    @Test
    void rejectionRePlansWithTheCommentThenApprovalExecutesTheNewPlan() {
        OrchestrationRun run = engine.startRun(GREENFIELD, true);

        engine.rejectPlan(run.getId(), "kazi", "put the count behind a cached service method");

        RunView replanned = engine.getRun(run.getId());
        assertThat(replanned.status()).isEqualTo(RunStatus.AWAITING_HUMAN.name());
        // the rejected plan is kept for the audit trail; a new plan waits for review
        assertThat(statuses(replanned, "DESIGN")).containsExactlyInAnyOrder("SUPERSEDED", "AWAITING_HUMAN");
        assertThat(waitingDesign(replanned).output().get("approach").asString())
                .startsWith("Revised after review (put the count behind a cached service method)");
        assertThat(replanned.auditTrail())
                .anySatisfy(t -> assertThat(t.reason()).contains("design started with 1 human decision(s)"))
                .anySatisfy(t -> assertThat(t.reason()).startsWith("re-planning after review (1/2)"));

        engine.approve(run.getId(), "kazi", "better");

        RunView done = engine.getRun(run.getId());
        assertThat(done.status()).isEqualTo(RunStatus.COMPLETED.name());
        assertThat(done.decisions()).extracting(RunView.DecisionView::decision)
                .containsExactly("Rejected: put the count behind a cached service method", "Approved: better");
    }

    @Test
    void rePlanningIsBoundedThenTheRunStopsSafely() {
        OrchestrationRun run = engine.startRun(GREENFIELD, true);

        engine.rejectPlan(run.getId(), "kazi", "no");
        engine.rejectPlan(run.getId(), "kazi", "still no");
        engine.rejectPlan(run.getId(), "kazi", "no again");          // third rejection > MAX_REPLANS (2)

        RunView view = engine.getRun(run.getId());
        assertThat(view.status()).isEqualTo(RunStatus.FAILED.name());
        assertThat(statuses(view, "DESIGN")).containsOnly("SUPERSEDED").hasSize(3);
        assertThat(statuses(view, "EXECUTE")).isEmpty();                            // nothing was executed
        assertThat(view.auditTrail())
                .anySatisfy(t -> assertThat(t.reason()).startsWith("safe-stop: plan rejected 3 times"));
    }

    @Test
    void rejectingWhileWaitingOnARequirementsQuestionIsAConflict() {
        OrchestrationRun run = engine.startRun("Make the system more scalable", true);   // waits on peak load

        assertThatThrownBy(() -> engine.rejectPlan(run.getId(), "kazi", "no"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("not for a plan review");
    }

    @Test
    void bothGatesInOneRunQuestionThenPlanReview() {
        OrchestrationRun run = engine.startRun("Make the system more scalable", true);

        engine.approve(run.getId(), "kazi", "Plan for 5,000 redirects per second");   // answers the question
        RunView planReady = engine.getRun(run.getId());
        assertThat(planReady.status()).isEqualTo(RunStatus.AWAITING_HUMAN.name());
        assertThat(waitingDesign(planReady).output().get("approach").asString()).contains("Redis");

        engine.approve(run.getId(), "kazi", "ship it");                              // approves the plan
        assertThat(engine.getRun(run.getId()).status()).isEqualTo(RunStatus.COMPLETED.name());
        assertThat(engine.getRun(run.getId()).decisions()).hasSize(2);
    }

    @Test
    void runsWithoutReviewBehaveAsBefore() {
        assertThat(engine.startRun(GREENFIELD).getStatus()).isEqualTo(RunStatus.COMPLETED);
    }

    private static List<String> statuses(RunView view, String typePrefix) {
        return view.tasks().stream()
                .filter(t -> t.taskType().startsWith(typePrefix))
                .map(RunView.TaskView::status)
                .toList();
    }

    private static RunView.TaskView waitingDesign(RunView view) {
        return view.tasks().stream()
                .filter(t -> t.taskType().equals("DESIGN") && t.status().equals("AWAITING_HUMAN"))
                .findFirst().orElseThrow();
    }
}

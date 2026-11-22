package com.schwab.urlshortener.orchestration.engine;

import com.schwab.urlshortener.orchestration.run.OrchestrationRun;
import com.schwab.urlshortener.orchestration.run.RunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three scenarios the brief asks for, end to end on the free stub agents.
 * Each one shows decomposition (the plan), orchestration (stages, gates, waves) and validation.
 */
@SpringBootTest
@Transactional
class ScenarioTest {

    @Autowired
    private OrchestrationEngine engine;

    @Test
    void greenfieldNewFeatureRunsWithoutAHuman() {
        OrchestrationRun run = engine.startRun("Add an endpoint that returns total clicks for a short code");

        RunView view = engine.getRun(run.getId());
        assertThat(view.status()).isEqualTo(RunStatus.COMPLETED.name());
        assertThat(view.decisions()).isEmpty();                                 // only LOW ambiguity: default used
        assertThat(design(view).output().get("approach").asString())
                .contains("AnalyticsEventRepository").contains("no schema change");
        assertThat(executeStatuses(view)).containsExactlyInAnyOrder(
                "EXECUTE T1:COMPLETED", "EXECUTE T2:COMPLETED", "EXECUTE T3:COMPLETED", "EXECUTE T4:COMPLETED");
    }

    @Test
    void brownfieldBugFixNeedsChangeControlApprovalForTheSchemaChange() {
        OrchestrationRun run = engine.startRun("Fix bug: custom short codes longer than 10 characters fail with a 500");

        // high-impact action (schema change) → stops for a person before anything is planned
        RunView waiting = engine.getRun(run.getId());
        assertThat(waiting.status()).isEqualTo(RunStatus.AWAITING_HUMAN.name());
        assertThat(waiting.auditTrail()).anySatisfy(t -> assertThat(t.reason()).contains("Approve this schema change?"));

        engine.approve(run.getId(), "kazi", "Approved: widen short_code to 20 characters");

        RunView done = engine.getRun(run.getId());
        assertThat(done.status()).isEqualTo(RunStatus.COMPLETED.name());
        assertThat(done.decisions()).singleElement()
                .satisfies(d -> assertThat(d.decision()).contains("widen short_code"));
        // codebase reasoning: the plan names the impacted modules and the data flow
        assertThat(design(done).output().get("approach").asString())
                .contains("UrlShortenerService.validateCustomCode")
                .contains("UrlMapping.shortCode")
                .contains("Flow: POST /api/v1/shorten");
        assertThat(executeStatuses(done)).hasSize(4).allMatch(s -> s.endsWith(":COMPLETED"));
    }

    @Test
    void ambiguousRequestStopsForAMissingBusinessFactAndTheAnswerShapesThePlan() {
        OrchestrationRun run = engine.startRun("Make the system more scalable");
        assertThat(engine.getRun(run.getId()).status()).isEqualTo(RunStatus.AWAITING_HUMAN.name());

        engine.approve(run.getId(), "kazi", "Plan for 5,000 redirects per second");

        RunView done = engine.getRun(run.getId());
        assertThat(done.status()).isEqualTo(RunStatus.COMPLETED.name());
        assertThat(design(done).output().get("approach").asString()).contains("Redis").contains("5,000");
        assertThat(executeStatuses(done)).hasSize(4);
    }

    private static RunView.TaskView design(RunView view) {
        return view.tasks().stream().filter(t -> t.taskType().equals("DESIGN")).findFirst().orElseThrow();
    }

    private static List<String> executeStatuses(RunView view) {
        return view.tasks().stream()
                .filter(t -> t.taskType().startsWith("EXECUTE"))
                .map(t -> t.taskType() + ":" + t.status())
                .toList();
    }
}

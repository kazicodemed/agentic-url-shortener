package com.schwab.urlshortener.orchestration.agent.requirements;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the prompt we send, without calling the model (free, no API key). */
class ClaudeRequirementsAgentTest {

    @Test
    void firstAttemptSendsOnlyTheRequest() {
        String text = ClaudeRequirementsAgent.userText("Make the system more scalable", List.of());

        assertThat(text).contains("Make the system more scalable").doesNotContain("rejected");
    }

    @Test
    void retrySendsTheValidatorErrorsBack() {
        String text = ClaudeRequirementsAgent.userText("Make the system more scalable",
                List.of("intent is missing", "ambiguities[0].impact is missing"));

        assertThat(text)
                .contains("rejected by the validator")
                .contains("- intent is missing")
                .contains("- ambiguities[0].impact is missing");
    }

    @Test
    void systemPromptTellsTheModelNotToInventBusinessFacts() {
        assertThat(ClaudeRequirementsAgent.SYSTEM)
                .contains("Never invent business facts")
                .contains("HIGH")
                .contains("Use [] for an empty list");
    }
}

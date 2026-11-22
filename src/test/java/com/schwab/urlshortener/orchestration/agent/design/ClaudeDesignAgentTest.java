package com.schwab.urlshortener.orchestration.agent.design;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the prompt we send, without calling the model (free, no API key). */
class ClaudeDesignAgentTest {

    private static final String SPEC = "{\"intent\":\"Improve scalability\"}";

    @Test
    void humanDecisionsArePassedAsFixedConstraints() {
        String text = ClaudeDesignAgent.userText(SPEC,
                List.of(new HumanDecision("What peak load must the system handle?", "Plan for 5,000 redirects per second")),
                List.of());

        assertThat(text)
                .contains("\"intent\":\"Improve scalability\"")
                .contains("Human decisions (fixed constraints)")
                .contains("Q: What peak load must the system handle?")
                .contains("A: Plan for 5,000 redirects per second")
                .doesNotContain("rejected");
    }

    @Test
    void noDecisionsIsSaidExplicitly() {
        assertThat(ClaudeDesignAgent.userText(SPEC, List.of(), List.of())).contains("- none");
        assertThat(ClaudeDesignAgent.userText(SPEC, null, null)).contains("- none");
    }

    @Test
    void retrySendsTheValidatorErrorsBack() {
        String text = ClaudeDesignAgent.userText(SPEC, List.of(),
                List.of("cycle among tasks: T2, T3; remove one dependency to break it"));

        assertThat(text).contains("rejected by the validator").contains("- cycle among tasks: T2, T3");
    }

    @Test
    void systemPromptStatesTheDagRulesTheValidatorEnforces() {
        assertThat(ClaudeDesignAgent.SYSTEM)
                .contains("never null")
                .contains("must not form a cycle")
                .contains("Human decisions are fixed constraints");
    }
}

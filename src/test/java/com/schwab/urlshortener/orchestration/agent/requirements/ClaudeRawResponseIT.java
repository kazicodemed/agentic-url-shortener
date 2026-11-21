package com.schwab.urlshortener.orchestration.agent.requirements;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Diagnostic only: one real call, prints the raw response without mapping it.
 * Run it on its own when the structured mapping fails, to see what the model actually returned.
 */
@Disabled("Diagnostic only, makes a paid call. Remove this line to print the raw model response.")
@SpringBootTest(properties = "orchestration.agents.mode=claude")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeRawResponseIT {

    @Autowired ChatClient.Builder builder;

    @Test
    void printRawResponse() {
        ChatResponse response = builder.build().prompt()
                .system(ClaudeRequirementsAgent.SYSTEM)
                .user(ClaudeRequirementsAgent.userText("Make the system more scalable", java.util.List.of()))
                .call()
                .chatResponse();

        System.out.println("===== RAW CLAUDE RESPONSE =====");
        System.out.println("model:   " + response.getMetadata().getModel());
        System.out.println("usage:   " + response.getMetadata().getUsage());
        System.out.println("results: " + response.getResults().size());
        for (Generation g : response.getResults()) {
            System.out.println("--- finishReason: " + g.getMetadata().getFinishReason());
            System.out.println("--- text: [" + g.getOutput().getText() + "]");
            System.out.println("--- message metadata: " + g.getOutput().getMetadata());
        }
        System.out.println("===============================");
    }
}

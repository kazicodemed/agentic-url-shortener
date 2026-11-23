package com.schwab.urlshortener.orchestration.execution;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Diagnostic: the executor's exact prompt, one real call, raw response printed (no mapping).
 * Run it on its own when ClaudeTaskExecutorIT reports "No content to map".
 */
@Disabled("Diagnostic only, makes a paid call. Remove this line to print the raw model response.")
@SpringBootTest(properties = "orchestration.agents.mode=claude")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeTaskExecutorRawIT {

    @Autowired ChatClient.Builder builder;

    @Test
    void printRawResponseForTheExecutorPrompt() {
        PlannedTask task = new PlannedTask("T2", "Invalidate cache on URL update or expiry",
                "Evict the cached UrlMapping when a mapping is deactivated or expires", List.of("T1"));

        ChatResponse response = builder.build().prompt()
                .system(ClaudeTaskExecutor.SYSTEM)
                .user(ClaudeTaskExecutor.userText(task)
                        + "\n\nAnswer only with JSON: {\"files\": [...], \"summary\": \"...\", \"diff\": \"...\"}")
                .call()
                .chatResponse();

        System.out.println("===== RAW EXECUTOR RESPONSE =====");
        System.out.println("model:   " + response.getMetadata().getModel());
        System.out.println("usage:   " + response.getMetadata().getUsage());
        System.out.println("results: " + response.getResults().size());
        for (Generation g : response.getResults()) {
            String text = g.getOutput().getText();
            System.out.println("--- finishReason: " + g.getMetadata().getFinishReason());
            System.out.println("--- text length:  " + (text == null ? "null" : text.length()));
            System.out.println("--- text: [" + text + "]");
            System.out.println("--- message metadata: " + g.getOutput().getMetadata());
        }
        System.out.println("=================================");
    }
}

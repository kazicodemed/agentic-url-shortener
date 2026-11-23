package com.schwab.urlshortener.orchestration.agent.requirements;

import com.schwab.urlshortener.orchestration.agent.ClaudeAnswers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Requirements agent backed by a real model (Claude via Spring AI).
 *
 * <p>Only created when {@code orchestration.agents.mode=claude}. The default is the free
 * {@link StubRequirementsAgent}, so tests and demos never call a paid API by accident.
 *
 * <p>The model's answer is mapped to {@link RequirementSpec} by Spring AI's structured
 * output. It is NOT trusted: {@code RequirementsStep} still runs the validator, and the
 * validator's errors come back here as {@code feedback} on the retry.
 */
@Component
@ConditionalOnProperty(name = "orchestration.agents.mode", havingValue = "claude")
public class ClaudeRequirementsAgent implements RequirementsAgent {

    static final String SYSTEM = """
            You are a requirements analyst for a URL shortener service (Spring Boot, Postgres, Redis).
            Turn the user's change request into a precise requirement spec.

            Rules:
            - intent: one sentence describing what the user wants.
            - functionalRequirements: concrete, testable statements; at most 10.
            - ambiguities: questions whose answer would change the solution.
              For each, give a proposedDefault you would use if nobody answers, and an impact:
                HIGH   = you cannot proceed safely without a person's answer
                         (a business fact such as peak load, budget, or a compliance rule).
                MEDIUM = it changes the design, but your default is reasonable.
                LOW    = a detail; your default is fine.
            - Never invent business facts such as traffic numbers. If one is needed, ask it as a HIGH ambiguity.
            - If the request is too unclear to list any requirement, return an empty list
              and ask the blocking question as a HIGH ambiguity.
            - Use [] for an empty list, never null.
            """;

    private final ChatClient chatClient;

    public ClaudeRequirementsAgent(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM).build();
    }

    @Override
    public RequirementSpec analyze(String rawRequirement, List<String> feedback) {
        return ClaudeAnswers.ask(chatClient, userText(rawRequirement, feedback), RequirementSpec.class);
    }

    /** The user message: the request, plus the validator's errors on a retry. */
    static String userText(String rawRequirement, List<String> feedback) {
        StringBuilder text = new StringBuilder("Change request:\n").append(rawRequirement);
        if (feedback != null && !feedback.isEmpty()) {
            text.append("\n\nYour previous answer was rejected by the validator. Fix these problems:\n");
            feedback.forEach(error -> text.append("- ").append(error).append('\n'));
        }
        return text.toString();
    }
}

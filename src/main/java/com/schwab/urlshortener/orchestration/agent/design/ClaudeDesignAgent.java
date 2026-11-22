package com.schwab.urlshortener.orchestration.agent.design;

import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Design agent backed by a real model (Claude via Spring AI).
 *
 * <p>Only created when {@code orchestration.agents.mode=claude}; the free {@link StubDesignAgent}
 * is the default. The plan it returns is NOT trusted: {@code DesignStep} runs
 * {@link DesignSpecValidator} (ids, dependencies, cycles, size) and feeds the errors back here.
 *
 * <p>Human decisions are passed as fixed constraints, so a person's answer from the
 * requirements stage shapes the plan — the same lineage the stub demonstrates.
 */
@Component
@ConditionalOnProperty(name = "orchestration.agents.mode", havingValue = "claude")
public class ClaudeDesignAgent implements DesignAgent {

    static final String SYSTEM = """
            You are a software architect for a URL shortener service: Java 21, Spring Boot,
            PostgreSQL, Redis. Key modules:
            - UrlShortenerController (POST /api/v1/shorten, GET /api/v1/analytics/{code}), RedirectController (GET /{code})
            - UrlShortenerService (shortenUrl, validateCustomCode, saveWithRetry), ShortCodeGenerator, AnalyticsRecorder
            - UrlMappingRepository, AnalyticsEventRepository; entities UrlMapping (table url_mappings), AnalyticsEvent

            Turn the requirement spec into an implementation plan.

            Rules:
            - approach: two or three sentences. Name the impacted modules and the data flow.
            - tasks: between 2 and 8 tasks, ids "T1", "T2", ... in order.
            - Each task: a short title, a one-sentence description, and dependsOn = ids of tasks
              that must finish first. Use [] when a task depends on nothing; never null.
            - dependsOn may only use ids from this plan, never the task's own id, and must not form a cycle.
            - Tasks that do not depend on each other should not be chained: they can run in parallel.
            - Human decisions are fixed constraints. Follow them exactly; do not reopen them.
            - Include at least one task for tests.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ClaudeDesignAgent(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.defaultSystem(SYSTEM).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public DesignSpec design(RequirementSpec requirements, List<HumanDecision> decisions, List<String> feedback) {
        return chatClient.prompt()
                .user(userText(objectMapper.writeValueAsString(requirements), decisions, feedback))
                .call()
                .entity(DesignSpec.class);
    }

    /** The user message: the spec, the human decisions, and the validator's errors on a retry. */
    static String userText(String requirementsJson, List<HumanDecision> decisions, List<String> feedback) {
        StringBuilder text = new StringBuilder("Requirement spec (JSON):\n").append(requirementsJson);

        text.append("\n\nHuman decisions (fixed constraints):\n");
        if (decisions == null || decisions.isEmpty()) {
            text.append("- none\n");
        } else {
            decisions.forEach(d -> text.append("- Q: ").append(d.question())
                    .append("\n  A: ").append(d.answer()).append('\n'));
        }

        if (feedback != null && !feedback.isEmpty()) {
            text.append("\nYour previous plan was rejected by the validator. Fix these problems:\n");
            feedback.forEach(error -> text.append("- ").append(error).append('\n'));
        }
        return text.toString();
    }
}

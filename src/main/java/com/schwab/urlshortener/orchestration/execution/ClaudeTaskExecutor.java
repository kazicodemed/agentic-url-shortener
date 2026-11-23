package com.schwab.urlshortener.orchestration.execution;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import com.schwab.urlshortener.orchestration.governance.AgentConcurrencyLimiter;
import com.schwab.urlshortener.orchestration.agent.ClaudeAnswers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Execution backed by a real model: for one planned task, Claude proposes the code change
 * (files + summary + diff). The proposal is stored for human review and never applied to the repo.
 *
 * <p>Only created when {@code orchestration.agents.mode=claude}; {@link StubTaskExecutor} is the default.
 * Tasks in the same wave call the model in parallel, so every call goes through
 * {@link AgentConcurrencyLimiter} (bounded in-flight calls, with a timeout).
 */
@Component
@ConditionalOnProperty(name = "orchestration.agents.mode", havingValue = "claude")
public class ClaudeTaskExecutor implements TaskExecutor {

    // Draft rules — edit freely; ClaudeTaskExecutorTest only pins the ones the code relies on.
    static final String SYSTEM = """
            You are a senior Java engineer on a URL shortener (Java 21, Spring Boot, PostgreSQL, Redis).
            Key modules: UrlShortenerController, RedirectController, UrlShortenerService,
            ShortCodeGenerator, AnalyticsRecorder, UrlMappingRepository, AnalyticsEventRepository,
            entities UrlMapping (url_mappings) and AnalyticsEvent.
            
            Code lives under src/main/java/com/schwab/urlshortener/ (packages: controller, service, repository, entity, dto).
            
            You receive ONE planned task. Propose the code change for that task only.

            Rules:
            - files: the repository paths you would change or add (at least one).
            - summary: one or two sentences on what the change does and why.
            - diff: the change as a unified diff or a short code snippet; never empty.
            - Do not implement other tasks from the plan, even if they look related.
            - Keep the change small and consistent with Spring Boot conventions; include tests
              only if the task is about tests.
            """;

    /** Limiter key: the TaskExecutor contract carries no run id, so execution calls share one bucket. */
    static final String LIMITER_KEY = "execution";

    private final ChatClient chatClient;
    private final AgentConcurrencyLimiter limiter;

    public ClaudeTaskExecutor(ChatClient.Builder builder, AgentConcurrencyLimiter limiter) {
        // The model may think before answering; max-tokens in application.yml leaves room for both.
        this.chatClient = builder.defaultSystem(SYSTEM).build();
        this.limiter = limiter;
    }

    @Override
    public TaskResult execute(PlannedTask task) {
        ProposedChange change;
        try {
            change = limiter.execute(LIMITER_KEY, "executor:" + task.id(), () -> ClaudeAnswers.ask(chatClient, userText(task), ProposedChange.class));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TaskResult.failed(task.id(), "interrupted while waiting for the model");
        } catch (Exception e) {
            // the call failed (network, auth, no permit in time, no text, unreadable JSON): a result, not a crash
            return TaskResult.failed(task.id(), "model call failed: " + e.getMessage());
        }
        return check(task.id(), change);
    }

    /** Guardrail in code: the provider enforces the shape, this checks the content is usable. */
    static TaskResult check(String taskId, ProposedChange change) {
        if (change == null || change.files() == null || change.files().isEmpty()) {
            return TaskResult.failed(taskId, "proposal names no files");
        }
        if (change.diff() == null || change.diff().isBlank()) {
            return TaskResult.failed(taskId, "proposal has no diff");
        }
        return TaskResult.ok(taskId, change.summary() + "\nFiles: " + change.files() + "\n" + change.diff());
    }

    static String userText(PlannedTask task) {
        return "Implement this planned task:\n"
                + "ID: " + task.id() + "\n"
                + "Title: " + task.title() + "\n"
                + "Description: " + task.description() + "\n"
                + "Propose the change for this task only.";
    }
}

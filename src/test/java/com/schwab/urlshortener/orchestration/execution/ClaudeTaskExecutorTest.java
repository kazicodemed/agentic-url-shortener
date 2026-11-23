package com.schwab.urlshortener.orchestration.execution;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Free tests: the prompt we send and the content checks on Claude's answer — no model call. */
class ClaudeTaskExecutorTest {

    private static final PlannedTask T2 = new PlannedTask("T2", "Invalidate cache on URL update or expiry",
            "Evict the entry when a mapping changes or expires", List.of("T1"));

    @Test
    void promptCarriesTheTaskAndScopesItToThisTaskOnly() {
        assertThat(ClaudeTaskExecutor.userText(T2))
                .contains("ID: T2")
                .contains("Title: Invalidate cache on URL update or expiry")
                .contains("Description: Evict the entry when a mapping changes or expires")
                .contains("this task only");
    }

    @Test
    void systemPromptAsksForWhatTheCheckRequires() {
        assertThat(ClaudeTaskExecutor.SYSTEM)
                .contains("files")
                .contains("diff")
                .contains("never empty")
                .contains("Do not implement other tasks");
    }

    @Test
    void usableProposalIsASuccessWithFilesAndDiffInTheOutput() {
        TaskResult result = ClaudeTaskExecutor.check("T2", new ProposedChange(
                List.of("src/main/java/com/schwab/urlshortener/service/UrlShortenerService.java"),
                "Evict the cached mapping when a URL is deactivated",
                "+ @CacheEvict(value = \"urls\", key = \"#shortCode\")"));

        assertThat(result.success()).isTrue();
        assertThat(result.output())
                .contains("Evict the cached mapping")
                .contains("UrlShortenerService.java")
                .contains("@CacheEvict");
    }

    @Test
    void proposalWithoutFilesFails() {
        assertThat(ClaudeTaskExecutor.check("T2", new ProposedChange(List.of(), "s", "+ x")))
                .isEqualTo(TaskResult.failed("T2", "proposal names no files"));
        assertThat(ClaudeTaskExecutor.check("T2", new ProposedChange(null, "s", "+ x")).success()).isFalse();
    }

    @Test
    void proposalWithoutADiffFails() {
        assertThat(ClaudeTaskExecutor.check("T2", new ProposedChange(List.of("A.java"), "s", "  ")))
                .isEqualTo(TaskResult.failed("T2", "proposal has no diff"));
    }

    @Test
    void noAnswerAtAllFails() {
        assertThat(ClaudeTaskExecutor.check("T2", null).success()).isFalse();
    }
}

package com.schwab.urlshortener.orchestration.execution;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in: one real model call, only when ANTHROPIC_API_KEY is set.
 * Asserts the contract (a usable proposal), not the wording.
 */
@SpringBootTest(properties = "orchestration.agents.mode=claude")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeTaskExecutorIT {

    @Autowired TaskExecutor executor;

    @Test
    void realModelProposesAChangeForOneTask() {
        PlannedTask task = new PlannedTask("T2", "Invalidate cache on URL update or expiry",
                "Evict the cached UrlMapping when a mapping is deactivated or expires", List.of("T1"));

        TaskResult result = executor.execute(task);

        System.out.println("===== CLAUDE PROPOSED CHANGE =====\n" + result + "\n==================================");

        assertThat(executor).isInstanceOf(ClaudeTaskExecutor.class);
        assertThat(result.success()).as(result.error()).isTrue();
        assertThat(result.output()).contains("Files:");
    }
}

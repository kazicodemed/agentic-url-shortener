package com.schwab.urlshortener.orchestration.agent.design;

import com.schwab.urlshortener.orchestration.agent.Impact;
import com.schwab.urlshortener.orchestration.agent.requirements.Ambiguity;
import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in: one real model call, only when ANTHROPIC_API_KEY is set.
 * Asserts the contract (a plan our validator accepts), not the wording.
 */
@SpringBootTest(properties = "orchestration.agents.mode=claude")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeDesignAgentIT {

    @Autowired DesignAgent agent;
    @Autowired DesignSpecValidator validator;

    @Test
    void realModelReturnsAPlanThatPassesTheValidator() {
        RequirementSpec spec = new RequirementSpec(
                "Improve the URL shortener's ability to handle more traffic",
                List.of("Cache redirect lookups so most requests skip the database",
                        "Keep p99 redirect latency under 50 ms at peak load"),
                List.of(new Ambiguity("What peak load must the system handle?", "1,000 redirects per second", Impact.HIGH)));

        DesignSpec plan = agent.design(spec,
                List.of(new HumanDecision("What peak load must the system handle?", "Plan for 5,000 redirects per second")),
                List.of());

        System.out.println("===== CLAUDE DESIGN PLAN =====\n" + plan + "\n==============================");

        assertThat(agent).isInstanceOf(ClaudeDesignAgent.class);
        assertThat(validator.validate(plan).errors()).isEmpty();
        assertThat(plan.tasks()).hasSizeBetween(2, 8);
    }
}

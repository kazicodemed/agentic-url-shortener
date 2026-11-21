package com.schwab.urlshortener.orchestration.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Autonomy boundary for agent/LLM calls.
 *
 * Virtual threads make orchestration threads effectively free, so the scarce
 * resource is the downstream LLM API (rate limits, cost). These limits cap how
 * much work agents may do at once, globally and per orchestration run.
 *
 * <pre>
 * orchestration:
 *   agent:
 *     max-concurrent-calls: 4          # across all runs
 *     max-concurrent-calls-per-run: 2  # one run cannot starve others
 *     acquire-timeout: 30s             # wait for a permit, then fail (retryable)
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "orchestration.agent")
public record AgentConcurrencyProperties(
        @DefaultValue("4") @Min(1) int maxConcurrentCalls,
        @DefaultValue("2") @Min(1) int maxConcurrentCallsPerRun,
        @DefaultValue("30s") @NotNull Duration acquireTimeout
) {
    public AgentConcurrencyProperties {
        if (maxConcurrentCallsPerRun > maxConcurrentCalls) {
            throw new IllegalArgumentException(
                    "orchestration.agent.max-concurrent-calls-per-run (" + maxConcurrentCallsPerRun
                            + ") must not exceed max-concurrent-calls (" + maxConcurrentCalls + ")");
        }
        if (acquireTimeout != null && acquireTimeout.isNegative()) {
            throw new IllegalArgumentException("orchestration.agent.acquire-timeout must not be negative");
        }
    }
}

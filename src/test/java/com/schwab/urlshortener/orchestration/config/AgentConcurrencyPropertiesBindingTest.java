package com.schwab.urlshortener.orchestration.config;

import com.schwab.urlshortener.orchestration.governance.AgentConcurrencyLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies YAML binding, defaults and fail-fast validation without starting the whole app.
 */
class AgentConcurrencyPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(OrchestrationConfig.class)
            .withBean(SimpleMeterRegistry.class)
            .withBean(AgentConcurrencyLimiter.class);

    @Test
    void usesDefaultsWhenNothingConfigured() {
        runner.run(ctx -> {
            AgentConcurrencyProperties props = ctx.getBean(AgentConcurrencyProperties.class);
            assertThat(props.maxConcurrentCalls()).isEqualTo(4);
            assertThat(props.maxConcurrentCallsPerRun()).isEqualTo(2);
            assertThat(props.acquireTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(ctx.getBean(AgentConcurrencyLimiter.class).availableGlobalPermits()).isEqualTo(4);
        });
    }

    @Test
    void bindsConfiguredValues() {
        runner.withPropertyValues(
                        "orchestration.agent.max-concurrent-calls=8",
                        "orchestration.agent.max-concurrent-calls-per-run=3",
                        "orchestration.agent.acquire-timeout=5s")
                .run(ctx -> {
                    AgentConcurrencyProperties props = ctx.getBean(AgentConcurrencyProperties.class);
                    assertThat(props.maxConcurrentCalls()).isEqualTo(8);
                    assertThat(props.maxConcurrentCallsPerRun()).isEqualTo(3);
                    assertThat(props.acquireTimeout()).isEqualTo(Duration.ofSeconds(5));
                });
    }

    @Test
    void failsStartupOnInvalidLimits() {
        runner.withPropertyValues("orchestration.agent.max-concurrent-calls=0")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues(
                        "orchestration.agent.max-concurrent-calls=2",
                        "orchestration.agent.max-concurrent-calls-per-run=5")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}

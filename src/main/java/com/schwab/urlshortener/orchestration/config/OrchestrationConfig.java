package com.schwab.urlshortener.orchestration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the orchestration layer.
 */
@Configuration
@EnableConfigurationProperties(AgentConcurrencyProperties.class)
public class OrchestrationConfig {
}

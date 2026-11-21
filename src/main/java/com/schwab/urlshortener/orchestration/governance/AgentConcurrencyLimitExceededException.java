package com.schwab.urlshortener.orchestration.governance;

import lombok.Getter;

import java.time.Duration;

/**
 * Thrown when an agent call could not get a permit within the acquire timeout.
 *
 * This is a transient condition: the orchestration engine should treat it as
 * retryable (bounded retry with backoff), not as a task failure.
 */
@Getter
public class AgentConcurrencyLimitExceededException extends RuntimeException {

    /** Which limit was saturated. */
    public enum Scope { RUN, GLOBAL }

    private final Scope scope;
    private final String runId;
    private final String agentName;

    public AgentConcurrencyLimitExceededException(Scope scope, String runId, String agentName, Duration timeout) {
        super("No " + scope.name().toLowerCase() + " agent permit for run=" + runId
                + " agent=" + agentName + " within " + timeout);
        this.scope = scope;
        this.runId = runId;
        this.agentName = agentName;
    }
}

package com.schwab.urlshortener.orchestration.agent.requirements;

import com.schwab.urlshortener.orchestration.agent.Impact;

public record Ambiguity (
    String question,
    String proposedDefault,
    Impact impact
) {}
package com.schwab.urlshortener.orchestration.step;

import java.util.List;

public record StepOutcome<T>(StepStatus status, T output, List<String> errors, int attempts) {}

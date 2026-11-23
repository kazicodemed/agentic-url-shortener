package com.schwab.urlshortener.orchestration.execution;

import java.util.List;

public record ProposedChange(List<String> files, String summary, String diff) {}
package com.schwab.urlshortener.orchestration.agent.design;

import java.util.List;

public record PlannedTask(String id, String title, String description, List<String> dependsOn) {}
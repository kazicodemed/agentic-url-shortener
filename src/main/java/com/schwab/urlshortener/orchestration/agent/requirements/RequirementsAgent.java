package com.schwab.urlshortener.orchestration.agent.requirements;

import java.util.List;

public interface RequirementsAgent {
    RequirementSpec analyze(String rawRequirement, List<String> feedback);
}

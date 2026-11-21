package com.schwab.urlshortener.orchestration.agent.requirements;

import java.util.List;

public record RequirementSpec (

    String intent,
    List<String> functionalRequirements,
    List<Ambiguity> ambiguities
) {

}

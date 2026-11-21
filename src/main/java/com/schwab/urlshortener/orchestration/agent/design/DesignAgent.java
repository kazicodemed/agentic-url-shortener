package com.schwab.urlshortener.orchestration.agent.design;

import com.schwab.urlshortener.orchestration.agent.requirements.RequirementSpec;

import java.util.List;

public interface DesignAgent   {
    public DesignSpec design(RequirementSpec requirements, List<HumanDecision> decisions, List<String> feedback);

}
package com.schwab.urlshortener.orchestration.agent.requirements;

import com.schwab.urlshortener.orchestration.agent.Impact;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest(properties = "orchestration.agents.mode=claude")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@Slf4j
@Disabled
class ClaudeRequirementsAgentIT {
    @Autowired RequirementsAgent agent;
    @Autowired
    RequirementSpecValidator validator;

    @Test
    void realModelReturnsASpecThatPassesTheValidator() {
        RequirementSpec spec = null;
        try {
            spec = agent.analyze("Make the system more scalable", List.of());
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }

        assertThat(validator.validate(spec).isValid()).isTrue();
        assertThat(spec.ambiguities()).anyMatch(a -> a.impact() == Impact.HIGH);  // it should ask about load
    }
}

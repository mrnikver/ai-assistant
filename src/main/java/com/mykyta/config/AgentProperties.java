package com.mykyta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configures safety limits for the explicit agent loop.
 *
 * @param supervisorMaxIterations maximum Supervisor decisions/delegations
 * @param knowledgeMaxIterations maximum Knowledge Agent decisions
 * @param runtimeMaxIterations maximum Runtime Agent decisions
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        @DefaultValue("4") int supervisorMaxIterations,
        @DefaultValue("3") int knowledgeMaxIterations,
        @DefaultValue("3") int runtimeMaxIterations
) {

    /** Rejects invalid limits early because zero would prevent any model decision. */
    public AgentProperties {
        if (supervisorMaxIterations < 1 || knowledgeMaxIterations < 1 || runtimeMaxIterations < 1) {
            throw new IllegalArgumentException("All agent iteration limits must be at least 1");
        }
    }
}

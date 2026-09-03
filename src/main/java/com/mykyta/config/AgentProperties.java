package com.mykyta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configures safety limits for the explicit agent loop.
 *
 * @param maxIterations maximum LLM reasoning/tool-execution iterations for one
 *                      user request; defaults to {@code 5} and must be positive
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        @DefaultValue("5") int maxIterations
) {

    /**
     * Rejects invalid limits early because zero would prevent any model decision.
     *
     * @param maxIterations configured maximum iteration count
     */
    public AgentProperties {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("agent.max-iterations must be at least 1");
        }
    }
}

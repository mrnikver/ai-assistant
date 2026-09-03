package com.mykyta.agent;

import com.mykyta.tool.Tool;

import java.util.List;

/**
 * Defines one bounded agent and the exact Java capabilities it may execute.
 *
 * @param name human-readable agent name used in logs and traces
 * @param type fixed responsibility of the agent
 * @param systemPrompt role and safety instruction supplied to the model
 * @param tools explicit capability allow-list for this agent
 * @param maxIterations maximum model decisions before execution is stopped
 * @param delegatedBy parent agent name, or {@code null} for the Supervisor
 */
public record AgentDefinition(
        String name,
        AgentType type,
        String systemPrompt,
        List<Tool> tools,
        int maxIterations,
        String delegatedBy
) {
    public AgentDefinition {
        tools = List.copyOf(tools);
        if (maxIterations < 1) throw new IllegalArgumentException("maxIterations must be at least 1");
    }
}

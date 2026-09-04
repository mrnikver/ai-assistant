package com.mykyta.model;

import com.mykyta.response.AssistantResponse;

/**
 * Summarizes a completed agent run for the calling application service.
 *
 * @param response final structured answer produced by the LLM
 * @param iterations number of LLM decisions made during the run
 * @param toolExecutions number of tool requests executed, including controlled failures
 * @param confirmationRequired typed terminal state produced by a guarded tool, if any
 */
public record AgentResult(
        AssistantResponse response,
        int iterations,
        int toolExecutions,
        PendingActionDetails confirmationRequired
) {
    public AgentResult(AssistantResponse response, int iterations, int toolExecutions) {
        this(response, iterations, toolExecutions, null);
    }
}

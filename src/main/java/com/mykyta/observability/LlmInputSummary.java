package com.mykyta.observability;

import java.util.List;

/** Sanitized bounded description of information supplied to an LLM. */
public record LlmInputSummary(
        String purpose,
        String agentName,
        String agentType,
        Integer iteration,
        String userRequest,
        int messageCount,
        int historyMessageCount,
        int memoryCount,
        List<String> availableTools,
        List<String> availableAgents,
        List<ObservationSummary> observations
) { }

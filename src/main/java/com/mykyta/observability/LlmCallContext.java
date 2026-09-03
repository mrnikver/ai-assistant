package com.mykyta.observability;

/** Explicit ownership and context counters for one model invocation. */
public record LlmCallContext(
        LlmPurpose purpose,
        String agentName,
        String agentType,
        Integer iteration,
        int historyMessageCount,
        int memoryCount,
        String userRequest
) {
    public static LlmCallContext memoryExtraction(String userRequest) {
        return new LlmCallContext(LlmPurpose.MEMORY_EXTRACTION, "Memory Extractor", "MEMORY", null, 0, 0, userRequest);
    }
}

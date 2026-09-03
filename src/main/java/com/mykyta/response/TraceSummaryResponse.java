package com.mykyta.response;

import com.mykyta.trace.TraceStatus;

/**
 * Compact trace information embedded in chat responses for zero-cost rendering.
 * @param traceId identifier used to load details
 * @param durationMs total execution duration
 * @param agentIterations recorded agent iterations
 * @param llmCalls recorded Ollama calls
 * @param toolCalls validated tool requests, including failures
 * @param knowledgeSearches knowledge retrieval operations
 * @param memoryLookups persistent-memory reads
 * @param status overall execution outcome
 */
public record TraceSummaryResponse(
        String traceId,
        long durationMs,
        int agentIterations,
        int llmCalls,
        int toolCalls,
        int knowledgeSearches,
        int memoryLookups,
        TraceStatus status
) {
}

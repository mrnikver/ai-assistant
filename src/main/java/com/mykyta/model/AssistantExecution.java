package com.mykyta.model;

import com.mykyta.response.AssistantResponse;
import com.mykyta.response.TraceSummaryResponse;

/**
 * Couples the user-facing answer with only the trace summary needed by chat.
 * @param response final answer produced by the agent
 * @param trace derived execution summary; full spans remain in the trace store
 */
public record AssistantExecution(AssistantResponse response, TraceSummaryResponse trace) {
}

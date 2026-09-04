package com.mykyta.response;

import com.mykyta.model.Confidence;
import com.mykyta.model.AssistantResponseStatus;
import com.mykyta.model.PendingActionDetails;

/**
 * Returns the answer plus a compact trace summary; detailed spans remain behind the trace endpoint.
 *
 * @param messageId stable identifier for the rendered assistant message
 * @param conversationId conversation that owns the answer
 * @param answer user-facing assistant content
 * @param confidence model-reported answer confidence
 * @param trace execution summary used to render the lazy details control
 * @param status application-owned response state
 * @param pendingAction structured action lifecycle details when this turn concerns a guarded action
 */
public record ChatResponse(
        String messageId,
        String conversationId,
        String answer,
        Confidence confidence,
        TraceSummaryResponse trace,
        AssistantResponseStatus status,
        PendingActionDetails pendingAction
) {
}

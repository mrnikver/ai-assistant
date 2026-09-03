package com.mykyta.response;

import com.mykyta.model.Confidence;

/**
 * Returns the answer plus a compact trace summary; detailed spans remain behind the trace endpoint.
 *
 * @param messageId stable identifier for the rendered assistant message
 * @param conversationId conversation that owns the answer
 * @param answer user-facing assistant content
 * @param confidence model-reported answer confidence
 * @param trace execution summary used to render the lazy details control
 */
public record ChatResponse(
        String messageId,
        String conversationId,
        String answer,
        Confidence confidence,
        TraceSummaryResponse trace
) {
}

package com.mykyta.trace;

import java.time.Instant;
import java.util.List;

/**
 * Represents a completed agent execution and all causally related observable spans.
 * @param traceId identifier shared with the chat response
 * @param status overall execution outcome
 * @param startedAt root operation start
 * @param endedAt root operation completion
 * @param durationMs total elapsed duration
 * @param spans immutable flat span set linked through parent IDs
 */
public record AgentTrace(
        String traceId,
        TraceStatus status,
        Instant startedAt,
        Instant endedAt,
        long durationMs,
        List<TraceSpan> spans
) {
}

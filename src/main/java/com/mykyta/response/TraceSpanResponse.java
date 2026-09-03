package com.mykyta.response;

import com.mykyta.trace.TraceSpanType;
import com.mykyta.trace.TraceStatus;

import java.time.Instant;
import java.util.Map;

/**
 * API representation of a span; parent IDs let clients reconstruct arbitrary depth.
 * @param spanId unique span identifier
 * @param parentSpanId causal parent or {@code null}
 * @param type operation category
 * @param name display label
 * @param status operation outcome
 * @param startedAt operation start
 * @param endedAt operation completion
 * @param durationMs elapsed duration
 * @param iteration agent-loop iteration where applicable
 * @param metadata sanitized operation details
 */
public record TraceSpanResponse(
        String spanId,
        String parentSpanId,
        TraceSpanType type,
        String name,
        TraceStatus status,
        Instant startedAt,
        Instant endedAt,
        long durationMs,
        Integer iteration,
        Map<String, Object> metadata
) {
}

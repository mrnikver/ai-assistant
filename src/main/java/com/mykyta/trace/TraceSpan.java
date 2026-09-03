package com.mykyta.trace;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable record of one observable operation and its causal parent.
 *
 * <p>Metadata is deliberately limited to operational facts. Prompts, memory values,
 * retrieved chunk text, credentials, and hidden model reasoning must never be stored.</p>
 *
 * @param spanId unique operation identifier within the trace
 * @param parentSpanId causal parent, or {@code null} for the root
 * @param type operation category used for summaries and presentation
 * @param name human-readable operation label
 * @param status completed operation outcome
 * @param startedAt wall-clock start
 * @param endedAt wall-clock completion
 * @param durationMs elapsed duration
 * @param iteration agent-loop iteration when applicable
 * @param metadata sanitized operational attributes
 */
public record TraceSpan(
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

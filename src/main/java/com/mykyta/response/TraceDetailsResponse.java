package com.mykyta.response;

import com.mykyta.trace.TraceStatus;

import java.time.Instant;
import java.util.List;

/**
 * Complete lazy-loaded trace DTO returned by the observability API.
 * @param traceId execution identifier
 * @param status overall outcome
 * @param startedAt execution start
 * @param endedAt execution completion
 * @param durationMs total duration
 * @param summary counters derived from spans
 * @param spans flat causal span set
 */
public record TraceDetailsResponse(
        String traceId,
        TraceStatus status,
        Instant startedAt,
        Instant endedAt,
        long durationMs,
        TraceSummaryResponse summary,
        List<TraceSpanResponse> spans
) {
}

package com.mykyta.service;

import com.mykyta.response.TraceDetailsResponse;
import com.mykyta.response.TraceSpanResponse;
import com.mykyta.response.TraceSummaryResponse;
import com.mykyta.trace.AgentTrace;
import com.mykyta.trace.TraceSpan;
import com.mykyta.trace.TraceSpanType;
import com.mykyta.trace.TraceStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Stores trace domain records and maps them to stable API response models. */
@Service
public class TraceService {
    private final TraceStore traceStore;

    /**
     * Creates the application-facing trace storage service.
     * @param traceStore bounded persistence boundary for completed traces
     */
    public TraceService(TraceStore traceStore) { this.traceStore = traceStore; }

    /**
     * Retains a completed trace and returns its derived chat summary.
     * @param trace completed trace
     * @return counters derived from recorded spans
     */
    public TraceSummaryResponse save(AgentTrace trace) {
        traceStore.save(trace);
        return summarize(trace);
    }

    /**
     * Loads a retained trace for lazy UI expansion.
     * @param traceId identifier returned by chat
     * @return API-safe trace hierarchy data
     * @throws ResponseStatusException when the trace was never stored or was evicted
     */
    public TraceDetailsResponse get(String traceId) {
        AgentTrace trace = traceStore.find(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution trace not found"));
        List<TraceSpanResponse> spans = trace.spans().stream().map(this::toResponse).toList();
        return new TraceDetailsResponse(trace.traceId(), trace.status(), trace.startedAt(), trace.endedAt(),
                trace.durationMs(), summarize(trace), spans);
    }

    private TraceSummaryResponse summarize(AgentTrace trace) {
        return new TraceSummaryResponse(trace.traceId(), trace.durationMs(), count(trace, TraceSpanType.AGENT_ITERATION),
                count(trace, TraceSpanType.LLM_CALL), count(trace, TraceSpanType.TOOL_CALL),
                count(trace, TraceSpanType.KNOWLEDGE_SEARCH), count(trace, TraceSpanType.MEMORY_LOOKUP), trace.status());
    }

    private int count(AgentTrace trace, TraceSpanType type) {
        return (int) trace.spans().stream().filter(span -> span.type() == type).count();
    }

    private TraceSpanResponse toResponse(TraceSpan span) {
        return new TraceSpanResponse(span.spanId(), span.parentSpanId(), span.type(), span.name(), span.status(),
                span.startedAt(), span.endedAt(), span.durationMs(), span.iteration(), span.metadata());
    }
}

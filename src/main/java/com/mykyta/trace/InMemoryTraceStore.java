package com.mykyta.trace;

import com.mykyta.config.TraceProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded single-instance trace store suitable for local operation.
 * Oldest entries are evicted when {@code trace.max-entries} is exceeded.
 */
@Component
public class InMemoryTraceStore implements TraceStore {
    private final int maxEntries;
    private final Map<String, AgentTrace> traces = new LinkedHashMap<>();

    /**
     * Creates a local store with deterministic oldest-first eviction.
     * @param properties configured upper bound for retained traces
     */
    public InMemoryTraceStore(TraceProperties properties) {
        this.maxEntries = properties.maxEntries();
    }

    @Override
    public synchronized void save(AgentTrace trace) {
        traces.put(trace.traceId(), trace);
        while (traces.size() > maxEntries) {
            traces.remove(traces.keySet().iterator().next());
        }
    }

    @Override
    public synchronized Optional<AgentTrace> find(String traceId) {
        return Optional.ofNullable(traces.get(traceId));
    }
}

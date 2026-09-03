package com.mykyta.trace;

import java.util.Optional;

/**
 * Persists completed traces beyond the chat request so details can be loaded lazily.
 * Implementations own retention and may later be replaced by durable or OpenTelemetry storage.
 */
public interface TraceStore {
    /**
     * Retains a completed trace according to the implementation's lifecycle policy.
     * @param trace completed trace to retain
     */
    void save(AgentTrace trace);

    /**
     * Finds a trace without exposing storage details to the API layer.
     * @param traceId public trace identifier
     * @return the retained trace, if it has not been evicted
     */
    Optional<AgentTrace> find(String traceId);
}

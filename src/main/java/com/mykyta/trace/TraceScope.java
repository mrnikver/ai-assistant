package com.mykyta.trace;

import java.util.Map;

/**
 * Mutable lifetime handle used to annotate and close an active span.
 * Try-with-resources guarantees duration capture when an operation exits exceptionally.
 */
public interface TraceScope extends AutoCloseable {
    /**
     * Adds one sanitized operational attribute.
     * @param key safe metadata key
     * @param value sanitized operational value
     * @return this scope
     */
    TraceScope metadata(String key, Object value);

    /**
     * Adds multiple sanitized operational attributes.
     * @param values sanitized operational metadata
     * @return this scope
     */
    TraceScope metadata(Map<String, ?> values);

    /**
     * Marks the operation as failed without recording a stack trace.
     * @param error failure used only to derive a safe error category
     */
    void fail(Throwable error);

    /** Completes duration and status recording. */
    @Override
    void close();
}

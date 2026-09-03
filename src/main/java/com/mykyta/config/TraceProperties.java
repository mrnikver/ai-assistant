package com.mykyta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configures the bounded lifecycle of locally retained execution traces.
 * @param maxEntries maximum retained traces; defaults to 500 and must be positive
 * @param llmPreviewMaxChars maximum characters in a sanitized LLM text preview
 */
@Validated
@ConfigurationProperties(prefix = "trace")
public record TraceProperties(
        @DefaultValue("500") int maxEntries,
        @DefaultValue("500") int llmPreviewMaxChars
) {
    /** Rejects invalid retention bounds during application startup. */
    public TraceProperties {
        if (maxEntries < 1 || llmPreviewMaxChars < 32) {
            throw new IllegalArgumentException("Trace bounds are invalid; preview length must be at least 32");
        }
    }
}

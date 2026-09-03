package com.mykyta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configures the bounded lifecycle of locally retained execution traces.
 * @param maxEntries maximum retained traces; defaults to 500 and must be positive
 */
@Validated
@ConfigurationProperties(prefix = "trace")
public record TraceProperties(
        @DefaultValue("500") int maxEntries
) {
    /** Rejects invalid retention bounds during application startup. */
    public TraceProperties {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("trace.max-entries must be at least 1");
        }
    }
}

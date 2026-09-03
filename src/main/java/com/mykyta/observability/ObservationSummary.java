package com.mykyta.observability;

import java.util.Map;

/** Bounded information returned by a tool or delegated agent. */
public record ObservationSummary(
        String source,
        Map<String, Object> arguments,
        String resultPreview,
        Integer resultCount
) { }

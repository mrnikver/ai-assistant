package com.mykyta.tool;

import java.util.Map;

/** Controlled tool output plus safe operational metadata for logs and traces. */
public record ToolExecutionOutcome(String content, Map<String, Object> metadata) {
    public ToolExecutionOutcome {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ToolExecutionOutcome observation(String content) {
        return new ToolExecutionOutcome(content, Map.of());
    }
}

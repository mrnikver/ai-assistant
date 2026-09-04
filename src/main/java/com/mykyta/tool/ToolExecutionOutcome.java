package com.mykyta.tool;

import com.mykyta.model.PendingActionDetails;

import java.util.Map;

/** Controlled tool output plus safe operational metadata for logs and traces. */
public record ToolExecutionOutcome(String content, Map<String, Object> metadata,
                                   PendingActionDetails confirmationRequired) {
    public ToolExecutionOutcome {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public ToolExecutionOutcome(String content, Map<String, Object> metadata) {
        this(content, metadata, null);
    }

    public static ToolExecutionOutcome observation(String content) {
        return new ToolExecutionOutcome(content, Map.of(), null);
    }

    public static ToolExecutionOutcome confirmationRequired(String content, Map<String, Object> metadata,
                                                            PendingActionDetails action) {
        return new ToolExecutionOutcome(content, metadata, action);
    }
}

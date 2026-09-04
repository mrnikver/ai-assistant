package com.mykyta.model;

import java.util.Map;

/** Structured action state propagated without language-model interpretation. */
public record PendingActionDetails(
        String pendingActionId,
        String tool,
        Map<String, Object> arguments,
        boolean confirmationRequired,
        PendingActionStatus confirmationStatus,
        String executionStatus,
        String message
) {
    public PendingActionDetails {
        arguments = Map.copyOf(arguments);
    }
}

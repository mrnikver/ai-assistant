package com.mykyta.model;

import java.time.Instant;
import java.util.Map;

/** Immutable snapshot of a validated state-changing tool request. */
public record PendingAction(
        String actionId,
        String conversationId,
        String toolName,
        Map<String, Object> arguments,
        PendingActionStatus status,
        Instant createdAt
) {
    public PendingAction {
        arguments = Map.copyOf(arguments);
    }

    public PendingAction withStatus(PendingActionStatus newStatus) {
        return new PendingAction(actionId, conversationId, toolName, arguments, newStatus, createdAt);
    }
}

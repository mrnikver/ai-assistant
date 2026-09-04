package com.mykyta.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-owned policy for restart targets and one-shot user confirmations.
 * Model-generated text can request an action but cannot authorize one.
 */
@Component
public class RestartConfirmationPolicy {
    private static final Map<String, Set<String>> ALLOWED_TARGETS = Map.of(
            "dev", Set.of("orders-service", "payments-service"),
            "local", Set.of("orders-service", "payments-service")
    );
    private static final Set<String> EXPLICIT_CONFIRMATIONS = Set.of(
            "yes", "yes restart it", "yes, restart it", "confirm", "confirmed", "proceed", "restart it"
    );

    private final Map<String, PendingRestart> pendingByConversation = new ConcurrentHashMap<>();

    public boolean isAllowed(String service, String environment) {
        return ALLOWED_TARGETS.getOrDefault(environment, Set.of()).contains(service);
    }

    /** Marks the existing pending action confirmed only for an unambiguous user reply. */
    public synchronized void acceptUserMessage(String conversationId, String userMessage) {
        if (conversationId == null || userMessage == null) return;
        String normalized = userMessage.trim().toLowerCase().replaceAll("[.!?]+$", "");
        PendingRestart pending = pendingByConversation.get(conversationId);
        if (pending != null && EXPLICIT_CONFIRMATIONS.contains(normalized)) {
            pendingByConversation.put(conversationId, pending.markConfirmed());
        }
    }

    /** Consumes a matching confirmation once, or records the requested target as pending. */
    public synchronized boolean consumeConfirmation(String conversationId, String service, String environment) {
        if (conversationId == null) return false;
        PendingRestart requested = new PendingRestart(service, environment, false);
        PendingRestart pending = pendingByConversation.get(conversationId);
        if (pending != null && pending.matches(requested) && pending.confirmed) {
            pendingByConversation.remove(conversationId);
            return true;
        }
        pendingByConversation.put(conversationId, requested);
        return false;
    }

    private record PendingRestart(String service, String environment, boolean confirmed) {
        private boolean matches(PendingRestart other) {
            return service.equals(other.service) && environment.equals(other.environment);
        }

        private PendingRestart markConfirmed() {
            return new PendingRestart(service, environment, true);
        }
    }
}

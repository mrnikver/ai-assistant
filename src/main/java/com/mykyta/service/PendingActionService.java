package com.mykyta.service;

import com.mykyta.model.PendingAction;
import com.mykyta.model.PendingActionStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stores validated actions and applies one-shot, conversation-bound confirmation transitions. */
@Service
public class PendingActionService {
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(15);
    private static final Set<String> SHORT_CONFIRMATIONS = Set.of(
            "yes", "yes restart it", "yes, restart it", "confirm", "confirmed", "proceed", "restart it"
    );

    private final Map<String, PendingAction> actionsById = new ConcurrentHashMap<>();
    private final Map<String, String> activeActionByConversation = new ConcurrentHashMap<>();

    public synchronized PendingAction create(String conversationId, String toolName, Map<String, Object> arguments) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Conversation ID is required for a confirmable action");
        }
        String previousId = activeActionByConversation.get(conversationId);
        if (previousId != null) {
            PendingAction previous = actionsById.get(previousId);
            if (previous != null && previous.status() == PendingActionStatus.AWAITING_CONFIRMATION) {
                if (previous.createdAt().plus(CONFIRMATION_TTL).isBefore(Instant.now())) {
                    actionsById.put(previousId, previous.withStatus(PendingActionStatus.EXPIRED));
                } else if (previous.toolName().equals(toolName) && previous.arguments().equals(arguments)) {
                    return previous;
                } else {
                    actionsById.put(previousId, previous.withStatus(PendingActionStatus.SUPERSEDED));
                }
            }
        }

        PendingAction action = new PendingAction(UUID.randomUUID().toString(), conversationId, toolName,
                arguments, PendingActionStatus.AWAITING_CONFIRMATION, Instant.now());
        actionsById.put(action.actionId(), action);
        activeActionByConversation.put(conversationId, action.actionId());
        return action;
    }

    /** Confirms only the action already active for this conversation; confirmation is never banked for later. */
    public synchronized ConfirmationResolution resolveConfirmation(String conversationId, String userMessage) {
        if (conversationId == null || !isExplicitConfirmation(userMessage)) {
            return ConfirmationResolution.none();
        }
        String actionId = activeActionByConversation.get(conversationId);
        if (actionId == null) return ConfirmationResolution.none();

        PendingAction action = actionsById.get(actionId);
        if (action == null) {
            activeActionByConversation.remove(conversationId, actionId);
            return ConfirmationResolution.none();
        }
        if (action.status() != PendingActionStatus.AWAITING_CONFIRMATION) {
            return new ConfirmationResolution(ResolutionStatus.ALREADY_RESOLVED, action);
        }
        if (action.createdAt().plus(CONFIRMATION_TTL).isBefore(Instant.now())) {
            PendingAction expired = action.withStatus(PendingActionStatus.EXPIRED);
            actionsById.put(actionId, expired);
            activeActionByConversation.remove(conversationId, actionId);
            return new ConfirmationResolution(ResolutionStatus.EXPIRED, expired);
        }

        PendingAction confirmed = action.withStatus(PendingActionStatus.CONFIRMED);
        actionsById.put(actionId, confirmed);
        return new ConfirmationResolution(ResolutionStatus.CONFIRMED, confirmed);
    }

    /** Atomically claims a confirmed action so stale or concurrent callers cannot execute it twice. */
    public synchronized PendingAction beginExecution(String actionId) {
        return transition(actionId, PendingActionStatus.CONFIRMED, PendingActionStatus.EXECUTING);
    }

    public synchronized PendingAction markExecuted(String actionId) {
        return transition(actionId, PendingActionStatus.EXECUTING, PendingActionStatus.EXECUTED);
    }

    public synchronized PendingAction markFailed(String actionId) {
        return transition(actionId, PendingActionStatus.EXECUTING, PendingActionStatus.FAILED);
    }

    public Optional<PendingAction> find(String actionId) {
        return Optional.ofNullable(actionsById.get(actionId));
    }

    private PendingAction transition(String actionId, PendingActionStatus expected, PendingActionStatus target) {
        PendingAction action = actionsById.get(actionId);
        if (action == null || action.status() != expected) {
            throw new IllegalStateException("Pending action is not in state " + expected);
        }
        PendingAction updated = action.withStatus(target);
        actionsById.put(actionId, updated);
        return updated;
    }

    private static boolean isExplicitConfirmation(String userMessage) {
        if (userMessage == null) return false;
        String normalized = userMessage.trim().toLowerCase().replaceAll("[.!?]+$", "");
        return SHORT_CONFIRMATIONS.contains(normalized)
                || normalized.matches("i confirm (the )?(pending )?(restart|action)")
                || normalized.matches("confirm (the )?(pending )?(restart|action)")
                || normalized.matches("please proceed with (the )?(pending )?(restart|action)");
    }

    public enum ResolutionStatus { NONE, CONFIRMED, EXPIRED, ALREADY_RESOLVED }

    public record ConfirmationResolution(ResolutionStatus status, PendingAction action) {
        private static ConfirmationResolution none() {
            return new ConfirmationResolution(ResolutionStatus.NONE, null);
        }
    }
}

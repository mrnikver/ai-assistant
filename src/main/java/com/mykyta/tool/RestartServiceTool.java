package com.mykyta.tool;

import com.mykyta.service.MockDeploymentService;
import com.mykyta.model.PendingAction;
import com.mykyta.service.PendingActionService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Guarded local/demo service restart capability. */
@Component
public class RestartServiceTool implements Tool {
    public static final String NAME = "restart_service";
    private static final Set<String> ARGUMENTS = Set.of("service", "environment");

    private final MockDeploymentService deploymentService;
    private static final Map<String, Set<String>> ALLOWED_TARGETS = Map.of(
            "dev", Set.of("orders-service", "payments-service"),
            "local", Set.of("orders-service", "payments-service")
    );
    private final PendingActionService pendingActionService;

    public RestartServiceTool(MockDeploymentService deploymentService,
                              PendingActionService pendingActionService) {
        this.deploymentService = deploymentService;
        this.pendingActionService = pendingActionService;
    }

    @Override public String name() { return NAME; }

    @Override public Map<String, Object> definition() {
        return Map.of("type", "function", "function", Map.of(
                "name", NAME,
                "description", "Restart a service when troubleshooting indicates that a restart may resolve the problem.",
                "parameters", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                                "service", Map.of("type", "string", "description", "Allowed service name"),
                                "environment", Map.of("type", "string", "enum", List.of("dev", "local"))
                        ),
                        "required", List.of("service", "environment")
                )));
    }

    @Override public String execute(Map<String, Object> arguments) {
        throw new ToolExecutionException("Restart requires application execution context",
                new IllegalStateException("Missing tool execution context"));
    }

    @Override public ToolExecutionOutcome execute(Map<String, Object> arguments, ToolExecutionContext context) {
        long startedAt = System.nanoTime();
        if (arguments == null || !ARGUMENTS.equals(arguments.keySet())) {
            throw new InvalidToolArgumentsException(
                    "Arguments must contain exactly 'service' and 'environment'");
        }
        String service = requireString(arguments, "service");
        String environment = requireString(arguments, "environment").toLowerCase();
        if (!ALLOWED_TARGETS.getOrDefault(environment, Set.of()).contains(service)) {
            throw new InvalidToolArgumentsException("Restart target is not allowed");
        }
        PendingAction action = pendingActionService.create(context.conversationId(), NAME,
                Map.of("service", service, "environment", environment));
        return outcome(action.actionId(), service, environment, "CONFIRMATION_REQUIRED",
                "Explicit user confirmation is required before restarting this service.",
                "AWAITING_CONFIRMATION", "NOT_EXECUTED", startedAt);
    }

    /** Executes only an already-confirmed application-owned action using its stored arguments. */
    public ToolExecutionOutcome executeConfirmed(PendingAction action) {
        long startedAt = System.nanoTime();
        if (!NAME.equals(action.toolName())) {
            throw new ToolExecutionException("Unsupported confirmed action",
                    new IllegalArgumentException(action.toolName()));
        }
        String service = (String) action.arguments().get("service");
        String environment = (String) action.arguments().get("environment");
        deploymentService.restartService(service, environment);
        return outcome(action.actionId(), service, environment, "RESTARTED", "Service restarted successfully.",
                "CONFIRMED", "COMPLETED", startedAt);
    }

    private static String requireString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new InvalidToolArgumentsException("Argument '" + name + "' must be a non-blank string");
        }
        return text.trim();
    }

    private static ToolExecutionOutcome outcome(String actionId, String service, String environment, String status,
                                                String message, String confirmationStatus,
                                                String executionStatus, long startedAt) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        String content = "{\"actionId\":\"%s\",\"service\":\"%s\",\"environment\":\"%s\",\"status\":\"%s\",\"message\":\"%s\"}"
                .formatted(actionId, service, environment, status, message);
        return new ToolExecutionOutcome(content, Map.of(
                "actionId", actionId,
                "tool", NAME,
                "arguments", Map.of("service", service, "environment", environment),
                "service", service,
                "environment", environment,
                "validationResult", "PASSED",
                "confirmationRequired", true,
                "confirmationStatus", confirmationStatus,
                "executionStatus", executionStatus,
                "executionDurationMs", durationMs
        ));
    }
}

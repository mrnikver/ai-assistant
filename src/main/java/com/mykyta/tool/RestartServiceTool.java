package com.mykyta.tool;

import com.mykyta.service.MockDeploymentService;
import com.mykyta.service.RestartConfirmationPolicy;
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
    private final RestartConfirmationPolicy confirmationPolicy;

    public RestartServiceTool(MockDeploymentService deploymentService,
                              RestartConfirmationPolicy confirmationPolicy) {
        this.deploymentService = deploymentService;
        this.confirmationPolicy = confirmationPolicy;
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
        if (!confirmationPolicy.isAllowed(service, environment)) {
            throw new InvalidToolArgumentsException("Restart target is not allowed");
        }

        if (!confirmationPolicy.consumeConfirmation(context.conversationId(), service, environment)) {
            return outcome(service, environment, "CONFIRMATION_REQUIRED",
                    "Explicit user confirmation is required before restarting this service.", false, startedAt);
        }

        deploymentService.restartService(service, environment);
        return outcome(service, environment, "RESTARTED", "Service restarted successfully.", true, startedAt);
    }

    private static String requireString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new InvalidToolArgumentsException("Argument '" + name + "' must be a non-blank string");
        }
        return text.trim();
    }

    private static ToolExecutionOutcome outcome(String service, String environment, String status,
                                                String message, boolean confirmed, long startedAt) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        String content = "{\"service\":\"%s\",\"environment\":\"%s\",\"status\":\"%s\",\"message\":\"%s\"}"
                .formatted(service, environment, status, message);
        return new ToolExecutionOutcome(content, Map.of(
                "service", service,
                "environment", environment,
                "validationResult", "PASSED",
                "confirmationRequired", true,
                "confirmationStatus", confirmed ? "CONFIRMED" : "MISSING",
                "executionStatus", status,
                "executionDurationMs", durationMs
        ));
    }
}

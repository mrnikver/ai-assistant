package com.mykyta.tool;

import com.mykyta.service.MockDeploymentService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Restored mocked tool that reports the current deployment state of a service. */
@Component
public class GetDeploymentStatusTool implements Tool {
    public static final String NAME = "getDeploymentStatus";
    private final MockDeploymentService deploymentService;

    public GetDeploymentStatusTool(MockDeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @Override public String name() { return NAME; }
    @Override public Map<String, Object> definition() {
        return definition(NAME, "Get the current deployment status for a service");
    }
    @Override public String execute(Map<String, Object> arguments) {
        return deploymentService.getDeploymentStatus(requireServiceName(arguments));
    }

    static Map<String, Object> definition(String name, String description) {
        return Map.of("type", "function", "function", Map.of(
                "name", name, "description", description,
                "parameters", Map.of("type", "object", "properties", Map.of(
                        "serviceName", Map.of("type", "string", "description", "Name of the service")
                ), "required", List.of("serviceName"))));
    }

    static String requireServiceName(Map<String, Object> arguments) {
        Object value = arguments.get("serviceName");
        if (!(value instanceof String serviceName) || serviceName.isBlank()) {
            throw new InvalidToolArgumentsException("Argument 'serviceName' must be a non-blank string");
        }
        return serviceName.trim();
    }
}

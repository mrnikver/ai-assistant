package com.mykyta.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ToolDispatcher {

    private final DeploymentTool deploymentTool;

    public ToolDispatcher(DeploymentTool deploymentTool) {
        this.deploymentTool = deploymentTool;
    }

    public String execute(String toolName, Map<String, Object> arguments) {

        log.debug("Dispatching tool: tool={}, argumentNames={}", toolName, arguments.keySet());

        String serviceName =
                (String) arguments.get("serviceName");

        return switch (toolName) {
            case "getDeploymentStatus" ->
                    deploymentTool.getDeploymentStatus(
                            (String) arguments.get("serviceName")
                    );

            case "getDeploymentLogs" ->
                    deploymentTool.getDeploymentLogs(
                            serviceName
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unknown tool: " + toolName
                    );
        };
    }
}

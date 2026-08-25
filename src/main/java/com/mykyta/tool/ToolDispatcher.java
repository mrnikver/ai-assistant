package com.mykyta.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolDispatcher {

    private final DeploymentTool deploymentTool;

    public ToolDispatcher(DeploymentTool deploymentTool) {
        this.deploymentTool = deploymentTool;
    }

    public String execute(String toolName, Map<String, Object> arguments) {

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
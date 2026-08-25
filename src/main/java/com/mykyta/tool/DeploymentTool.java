package com.mykyta.tool;

import org.springframework.stereotype.Component;

@Component
public class DeploymentTool {

    public String getDeploymentStatus(String serviceName) {
        return switch (serviceName) {
            case "payments-service" -> "FAILED";
            case "orders-service" -> "RUNNING";
            default -> "UNKNOWN";
        };
    }

    public String getDeploymentLogs(String serviceName) {
        return switch (serviceName) {
            case "payments-service" ->
                    "ERROR: Database connection refused";
            case "orders-service" ->
                    "Deployment completed successfully";
            default ->
                    "No deployment logs found";
        };
    }
}
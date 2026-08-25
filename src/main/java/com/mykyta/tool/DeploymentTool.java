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
}
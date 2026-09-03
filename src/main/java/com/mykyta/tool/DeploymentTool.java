package com.mykyta.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeploymentTool {

    public String getDeploymentStatus(String serviceName) {
        log.debug("Looking up deployment status: service={}", serviceName);
        return switch (serviceName) {
            case "payments-service" -> "FAILED";
            case "orders-service" -> "RUNNING";
            default -> "UNKNOWN";
        };
    }

    public String getDeploymentLogs(String serviceName) {
        log.debug("Looking up deployment logs: service={}", serviceName);
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

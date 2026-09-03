package com.mykyta.service;

import org.springframework.stereotype.Service;

/** Restored deterministic operational data used by the runtime investigation tools. */
@Service
public class MockDeploymentService {
    public String getDeploymentStatus(String serviceName) {
        return switch (serviceName) {
            case "payments-service" -> "FAILED";
            case "orders-service" -> "RUNNING";
            default -> "UNKNOWN";
        };
    }

    public String getDeploymentLogs(String serviceName) {
        return switch (serviceName) {
            case "payments-service" -> "ERROR: Database connection refused";
            case "orders-service" -> "Deployment completed successfully";
            default -> "No deployment logs found";
        };
    }
}

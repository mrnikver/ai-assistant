package com.mykyta.tool;

import com.mykyta.service.MockDeploymentService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Restored mocked tool that reports recent deployment logs for a service. */
@Component
public class GetDeploymentLogsTool implements Tool {
    public static final String NAME = "getDeploymentLogs";
    private final MockDeploymentService deploymentService;

    public GetDeploymentLogsTool(MockDeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @Override public String name() { return NAME; }
    @Override public Map<String, Object> definition() {
        return GetDeploymentStatusTool.definition(NAME,
                "Get recent deployment logs for a service. Use this to investigate deployment failures.");
    }
    @Override public String execute(Map<String, Object> arguments) {
        return deploymentService.getDeploymentLogs(GetDeploymentStatusTool.requireServiceName(arguments));
    }
}

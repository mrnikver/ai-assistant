package com.mykyta.agent;

import com.mykyta.config.AgentProperties;
import com.mykyta.model.AgentResult;
import com.mykyta.model.LLMMessage;
import com.mykyta.tool.GetDeploymentLogsTool;
import com.mykyta.tool.GetDeploymentStatusTool;
import com.mykyta.tool.RestartServiceTool;
import com.mykyta.tool.ToolExecutionContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/** Independent operational specialist with access only to restored mock runtime tools. */
@Service
public class RuntimeAgent {
    private static final String PROMPT = """
            You are the Runtime Agent, a current operational-state investigation specialist.
            Use the available mocked runtime tools for service deployment status and logs.
            A restart is appropriate only after investigation. If restart_service returns
            CONFIRMATION_REQUIRED, report that explicit user confirmation is needed and do not retry it.
            Treat their results as runtime mock data, never as documentation or historical truth.
            You cannot search project documentation and must not invent it.
            Return JSON with exactly "answer" and "confidence" (LOW, MEDIUM, or HIGH).
            """;
    private final AgentRuntime runtime;
    private final AgentDefinition definition;

    public RuntimeAgent(AgentRuntime runtime, GetDeploymentStatusTool statusTool,
                        GetDeploymentLogsTool logsTool, RestartServiceTool restartServiceTool,
                        AgentProperties properties) {
        this.runtime = runtime;
        this.definition = new AgentDefinition("Runtime Agent", AgentType.RUNTIME, PROMPT,
                List.of(statusTool, logsTool, restartServiceTool), properties.runtimeMaxIterations(), "Supervisor Agent");
    }

    public AgentResult investigate(String task) throws IOException, InterruptedException {
        return investigate(task, ToolExecutionContext.NONE);
    }

    public AgentResult investigate(String task, ToolExecutionContext executionContext)
            throws IOException, InterruptedException {
        return runtime.run(definition, List.of(new LLMMessage("user", task)), 0, 0, task, executionContext);
    }
}

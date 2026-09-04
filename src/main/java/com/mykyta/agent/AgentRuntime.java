package com.mykyta.agent;

import com.mykyta.client.LLMClient;
import com.mykyta.exception.AgentIterationLimitException;
import com.mykyta.model.AgentResult;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.ToolCall;
import com.mykyta.model.ToolResult;
import com.mykyta.observability.LlmCallContext;
import com.mykyta.observability.LlmPurpose;
import com.mykyta.observability.LlmObservabilitySanitizer;
import com.mykyta.response.AssistantResponse;
import com.mykyta.tool.ToolRegistry;
import com.mykyta.tool.ToolExecutionContext;
import com.mykyta.trace.AgentTracer;
import com.mykyta.trace.TraceScope;
import com.mykyta.trace.TraceSpanType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Executes the reusable bounded LLM/tool loop for one explicitly defined agent. */
@Service
public class AgentRuntime {
    private final LLMClient llmClient;
    private final AgentTracer agentTracer;
    private final LlmObservabilitySanitizer sanitizer;

    public AgentRuntime(LLMClient llmClient, AgentTracer agentTracer, LlmObservabilitySanitizer sanitizer) {
        this.llmClient = llmClient;
        this.agentTracer = agentTracer;
        this.sanitizer = sanitizer;
    }

    /** Runs an isolated agent using only the tools present in its definition. */
    public AgentResult run(AgentDefinition definition, List<LLMMessage> suppliedContext,
                           int historyMessageCount, int memoryCount, String userRequest)
            throws IOException, InterruptedException {
        return run(definition, suppliedContext, historyMessageCount, memoryCount, userRequest,
                ToolExecutionContext.NONE);
    }

    /** Runs an isolated agent with application-owned execution context for guarded tools. */
    public AgentResult run(AgentDefinition definition, List<LLMMessage> suppliedContext,
                           int historyMessageCount, int memoryCount, String userRequest,
                           ToolExecutionContext executionContext)
            throws IOException, InterruptedException {
        List<LLMMessage> context = new ArrayList<>();
        context.add(new LLMMessage("system", definition.systemPrompt()));
        context.addAll(suppliedContext);
        ToolRegistry registry = new ToolRegistry(definition.tools(), agentTracer, sanitizer, executionContext);
        TraceSpanType spanType = definition.type() == AgentType.SUPERVISOR
                ? TraceSpanType.SUPERVISOR : TraceSpanType.AGENT;

        try (TraceScope agentSpan = agentTracer.startSpan(spanType, definition.name())) {
            agentSpan.metadata("agentName", definition.name());
            agentSpan.metadata("agentType", definition.type().name());
            agentSpan.metadata("delegatedBy", definition.delegatedBy());
            agentSpan.metadata("allowedTools", definition.tools().stream().map(tool -> tool.name()).toList());
            agentSpan.metadata("maxIterations", definition.maxIterations());

            int toolExecutions = 0;
            for (int iteration = 1; iteration <= definition.maxIterations(); iteration++) {
                try (TraceScope iterationSpan = agentTracer.startSpan(
                        TraceSpanType.AGENT_ITERATION, definition.name() + " iteration")) {
                    iterationSpan.metadata("agentName", definition.name());
                    iterationSpan.metadata("agentType", definition.type().name());
                    iterationSpan.metadata("iteration", iteration);
                    LLMMessage response;
                    try {
                        boolean hasObservations = context.stream().anyMatch(message -> "tool".equals(message.role()));
                        LlmPurpose purpose = definition.type() == AgentType.SUPERVISOR && hasObservations
                                ? LlmPurpose.SUPERVISOR_SYNTHESIS : LlmPurpose.AGENT_DECISION;
                        LlmCallContext callContext = new LlmCallContext(purpose, definition.name(),
                                definition.type().name(), iteration, historyMessageCount, memoryCount, userRequest);
                        response = llmClient.chatWithTools(context, registry.definitions(), callContext);
                    } catch (IOException | InterruptedException exception) {
                        iterationSpan.fail(exception);
                        throw exception;
                    }

                    List<ToolCall> calls = response.toolCalls();
                    if (calls == null || calls.isEmpty()) {
                        AssistantResponse answer = llmClient.parseAssistantResponse(response);
                        return new AgentResult(answer, iteration, toolExecutions);
                    }

                    context.add(response);
                    for (ToolCall call : calls) {
                        ToolResult result = registry.execute(call);
                        toolExecutions++;
                        if (result.confirmationRequired() != null) {
                            iterationSpan.metadata("loopStopped", true);
                            iterationSpan.metadata("stopReason", "CONFIRMATION_REQUIRED");
                            iterationSpan.metadata("pendingActionId", result.confirmationRequired().pendingActionId());
                            agentSpan.metadata("loopStopped", true);
                            agentSpan.metadata("stopReason", "CONFIRMATION_REQUIRED");
                            agentSpan.metadata("pendingActionId", result.confirmationRequired().pendingActionId());
                            return new AgentResult(
                                    new AssistantResponse(result.confirmationRequired().message(),
                                            com.mykyta.model.Confidence.HIGH),
                                    iteration, toolExecutions, result.confirmationRequired());
                        }
                        String toolName = call.function() == null ? "unknown" : call.function().name();
                        context.add(new LLMMessage("tool", result.asObservation(), null, toolName));
                    }
                }
            }
            AgentIterationLimitException exception = new AgentIterationLimitException(definition.maxIterations());
            agentSpan.fail(exception);
            throw exception;
        }
    }
}

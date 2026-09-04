package com.mykyta.tool;

import com.mykyta.model.ToolCall;
import com.mykyta.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import com.mykyta.trace.AgentTracer;
import com.mykyta.trace.TraceScope;
import com.mykyta.trace.TraceSpanType;
import com.mykyta.observability.LlmObservabilitySanitizer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maintains the allow-list of Java capabilities available to the agent.
 *
 * <p>This registry is the primary safety boundary between model output and
 * executable application code. The LLM may request a function by name, but only
 * an explicitly registered {@link Tool} can run. Unknown names, malformed calls,
 * invalid arguments, and operational failures are converted into controlled
 * {@link ToolResult} observations rather than arbitrary method invocation or an
 * application crash.</p>
 *
 * <p>The complete action cycle is: the LLM chooses an action; this registry
 * validates it; the registered tool executes; its result becomes an observation;
 * and the LLM receives that observation on the next agent iteration.</p>
 */
@Slf4j
public class ToolRegistry {

    private final Map<String, Tool> tools;
    private final AgentTracer agentTracer;
    private final LlmObservabilitySanitizer sanitizer;
    private final ToolExecutionContext executionContext;

    /**
     * Builds an agent-scoped registry from its explicit tools and rejects duplicate names.
     *
     * @param registeredTools explicitly registered application tools
     * @param agentTracer collector used to record allowed and rejected tool requests
     * @throws IllegalStateException if two tools expose the same name
     */
    public ToolRegistry(List<Tool> registeredTools, AgentTracer agentTracer,
                        LlmObservabilitySanitizer sanitizer, ToolExecutionContext executionContext) {
        this.agentTracer = agentTracer;
        this.sanitizer = sanitizer;
        this.executionContext = executionContext;
        this.tools = Collections.unmodifiableMap(registeredTools.stream().collect(Collectors.toMap(
                Tool::name,
                Function.identity(),
                (first, duplicate) -> {
                    throw new IllegalStateException("Duplicate tool name: " + duplicate.name());
                },
                LinkedHashMap::new
        )));
    }

    /**
     * Returns descriptions for exactly the tools this registry permits to execute.
     *
     * @return tool definitions in deterministic registration order
     */
    public List<Map<String, Object>> definitions() {
        return tools.values().stream().map(Tool::definition).toList();
    }

    /**
     * Executes a tool requested by the language model through the registry allow-list.
     *
     * @param toolCall tool request produced by the language model
     * @return success or controlled failure to add back to the agent conversation
     */
    public ToolResult execute(ToolCall toolCall) {
        String requestedName = toolCall == null || toolCall.function() == null
                ? "unknown" : String.valueOf(toolCall.function().name());
        try (TraceScope span = agentTracer.startSpan(TraceSpanType.TOOL_CALL, "Tool: " + requestedName)) {
            span.metadata("toolName", requestedName);
            span.metadata("iteration", agentTracer.currentIteration());
            ToolResult result = executeRegistered(toolCall, span);
            span.metadata("successful", result.successful());
            span.metadata("resultLength", result.content().length());
            span.metadata("resultSummary", result.successful() ? "Observation returned" : "Controlled failure returned");
            if (!result.successful()) {
                span.metadata("error", result.content());
                span.fail(new IllegalStateException("Controlled tool failure"));
            }
            return result;
        }
    }

    private ToolResult executeRegistered(ToolCall toolCall, TraceScope span) {
        if (toolCall == null || toolCall.function() == null || toolCall.function().name() == null) {
            span.metadata("argumentNames", List.of());
            return ToolResult.failure("unknown", "Malformed tool call: function name is required");
        }

        String toolName = toolCall.function().name();
        Tool tool = tools.get(toolName);
        if (tool == null) {
            log.warn("LLM requested unknown tool: tool={}", toolName);
            return ToolResult.failure(toolName, "Unknown tool: " + toolName);
        }

        Map<String, Object> arguments = toolCall.function().arguments() == null
                ? Map.of()
                : toolCall.function().arguments();
        span.metadata("argumentNames", arguments.keySet().stream().sorted().toList());
        span.metadata("arguments", sanitizer.map(arguments));

        try {
            ToolExecutionOutcome outcome = tool.execute(arguments, executionContext);
            span.metadata(outcome.metadata());
            if (RestartServiceTool.NAME.equals(toolName)) {
                log.info("Restart tool evaluated: service={}, environment={}, validationResult={}, "
                                + "confirmationRequired={}, confirmationStatus={}, executionStatus={}, executionDurationMs={}",
                        outcome.metadata().get("service"), outcome.metadata().get("environment"),
                        outcome.metadata().get("validationResult"), outcome.metadata().get("confirmationRequired"),
                        outcome.metadata().get("confirmationStatus"), outcome.metadata().get("executionStatus"),
                        outcome.metadata().get("executionDurationMs"));
            }
            return ToolResult.success(toolName, outcome.content());
        } catch (InvalidToolArgumentsException exception) {
            span.metadata("validationResult", "REJECTED");
            span.metadata("confirmationRequired", false);
            span.metadata("confirmationStatus", "NOT_EVALUATED");
            span.metadata("executionStatus", "NOT_EXECUTED");
            span.metadata("service", sanitizer.preview(String.valueOf(arguments.get("service"))));
            span.metadata("environment", sanitizer.preview(String.valueOf(arguments.get("environment"))));
            log.warn("Tool arguments rejected: tool={}, argumentNames={}, reason={}",
                    toolName, arguments.keySet(), exception.getMessage());
            if (RestartServiceTool.NAME.equals(toolName)) {
                log.warn("Restart tool rejected: service={}, environment={}, validationResult=REJECTED, "
                                + "confirmationRequired=false, confirmationStatus=NOT_EVALUATED, "
                                + "executionStatus=NOT_EXECUTED",
                        sanitizer.preview(String.valueOf(arguments.get("service"))),
                        sanitizer.preview(String.valueOf(arguments.get("environment"))));
            }
            return ToolResult.failure(toolName, exception.getMessage());
        } catch (ToolExecutionException exception) {
            log.error("Tool execution failed: tool={}, argumentNames={}", toolName, arguments.keySet(), exception);
            return ToolResult.failure(toolName, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Unexpected tool failure: tool={}, argumentNames={}", toolName, arguments.keySet(), exception);
            return ToolResult.failure(toolName, "Tool execution failed");
        }
    }
}

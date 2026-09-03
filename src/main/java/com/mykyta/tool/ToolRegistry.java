package com.mykyta.tool;

import com.mykyta.model.ToolCall;
import com.mykyta.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
@Component
@Slf4j
public class ToolRegistry {

    private final Map<String, Tool> tools;

    /**
     * Builds a registry from Spring-managed tools and rejects duplicate names at startup.
     *
     * @param registeredTools explicitly registered application tools
     * @throws IllegalStateException if two tools expose the same name
     */
    public ToolRegistry(List<Tool> registeredTools) {
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
        if (toolCall == null || toolCall.function() == null || toolCall.function().name() == null) {
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

        try {
            return ToolResult.success(toolName, tool.execute(arguments));
        } catch (InvalidToolArgumentsException exception) {
            log.warn("Tool arguments rejected: tool={}, argumentNames={}, reason={}",
                    toolName, arguments.keySet(), exception.getMessage());
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

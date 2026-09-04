package com.mykyta.tool;

import java.util.Map;

/**
 * Defines a Java capability that may be requested by the language model.
 *
 * <p>A tool is an application-controlled action boundary. The LLM can choose an
 * action and supply arguments, but it cannot invoke arbitrary Java methods. A
 * {@link ToolRegistry} validates the requested name against registered tools,
 * invokes the matching implementation, and returns the result as an observation
 * for the next LLM turn.</p>
 *
 * <p>A new tool becomes available to the agent by implementing this interface
 * and registering the implementation as a Spring bean:</p>
 * <pre>{@code
 * @Component
 * class ServiceHealthTool implements Tool {
 *     // name(), definition(), and execute(...)
 * }
 * }</pre>
 */
public interface Tool {

    /**
     * Returns the stable function name used in LLM tool calls.
     *
     * @return unique tool name
     */
    String name();

    /**
     * Describes the function and its JSON input schema in Ollama's tool format.
     *
     * @return immutable or caller-safe tool definition sent to the LLM
     */
    Map<String, Object> definition();

    /**
     * Executes the application capability after the registry selects this tool.
     *
     * @param arguments arguments produced by the LLM
     * @return observation to add to the agent conversation
     * @throws InvalidToolArgumentsException when arguments do not satisfy the tool contract
     * @throws ToolExecutionException when the underlying capability cannot complete
     */
    String execute(Map<String, Object> arguments);

    /**
     * Executes with trusted request context and may expose safe trace metadata.
     * Existing read-only tools use the context-free implementation by default.
     */
    default ToolExecutionOutcome execute(Map<String, Object> arguments, ToolExecutionContext context) {
        return ToolExecutionOutcome.observation(execute(arguments));
    }
}

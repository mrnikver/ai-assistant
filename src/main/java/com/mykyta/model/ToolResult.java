package com.mykyta.model;

/**
 * Captures the controlled outcome of a tool request.
 *
 * <p>Both successful values and safe error descriptions become observations in
 * the conversation. This lets the LLM decide whether to retry with corrected
 * arguments, choose another action, or answer with the available information.</p>
 *
 * @param toolName requested tool name
 * @param successful whether execution completed successfully
 * @param content result or safe error description returned to the model
 */
public record ToolResult(String toolName, boolean successful, String content) {

    /**
     * Creates a successful observation.
     *
     * @param toolName executed tool name
     * @param content tool output
     * @return successful tool result
     */
    public static ToolResult success(String toolName, String content) {
        return new ToolResult(toolName, true, content);
    }

    /**
     * Creates a controlled failure observation.
     *
     * @param toolName requested tool name
     * @param message safe error message
     * @return failed tool result
     */
    public static ToolResult failure(String toolName, String message) {
        return new ToolResult(toolName, false, message);
    }

    /**
     * Formats the result for the LLM without exposing Java exceptions or stack traces.
     *
     * @return textual observation for a message with role {@code tool}
     */
    public String asObservation() {
        return successful ? content : "Tool error: " + content;
    }
}

package com.mykyta.tool;

/** Application-owned context supplied to tools; model output cannot populate these values. */
public record ToolExecutionContext(String conversationId) {
    public static final ToolExecutionContext NONE = new ToolExecutionContext(null);
}

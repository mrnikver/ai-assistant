package com.mykyta.model;

/**
 * Represents one application action requested by the LLM.
 *
 * <p>The request is inert data until {@code ToolRegistry} validates its function
 * name against explicitly registered tools.</p>
 *
 * @param id optional provider-generated call identifier
 * @param function requested function name and arguments
 */
public record ToolCall(
        String id,
        ToolFunction function
) {}

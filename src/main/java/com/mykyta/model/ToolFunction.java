package com.mykyta.model;

import java.util.Map;

/**
 * Describes the function portion of an Ollama tool call.
 *
 * @param index optional provider ordering index
 * @param name requested registered-tool name
 * @param arguments untrusted arguments produced by the model and validated by the tool
 */
public record ToolFunction(
        Integer index,
        String name,
        Map<String, Object> arguments
) {}

package com.mykyta.observability;

import java.util.Map;

/** Sanitized tool or delegation request emitted by a model. */
public record ToolCallSummary(String name, Map<String, Object> arguments) { }

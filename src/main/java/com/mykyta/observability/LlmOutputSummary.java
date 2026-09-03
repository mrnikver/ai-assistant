package com.mykyta.observability;

import java.util.List;
import java.util.Map;

/** Sanitized bounded description of an LLM result. */
public record LlmOutputSummary(
        LlmOutputType type,
        List<ToolCallSummary> toolCalls,
        String answerPreview,
        String confidence,
        String errorType,
        Map<String, Object> structuredResult
) { }

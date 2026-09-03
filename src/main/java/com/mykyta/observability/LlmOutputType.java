package com.mykyta.observability;

/** Observable application-level result of an LLM call. */
public enum LlmOutputType {
    TOOL_CALL,
    DELEGATION,
    FINAL_RESPONSE,
    STRUCTURED_RESULT,
    PLAIN_TEXT_FALLBACK,
    ERROR
}

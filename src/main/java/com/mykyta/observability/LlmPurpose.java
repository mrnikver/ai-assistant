package com.mykyta.observability;

/** Semantic reason an Ollama chat invocation occurred. */
public enum LlmPurpose {
    MEMORY_EXTRACTION,
    AGENT_DECISION,
    SUPERVISOR_SYNTHESIS
}

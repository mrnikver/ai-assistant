package com.mykyta.trace;

/** Identifies an observable operation in an agent execution without exposing model reasoning. */
public enum TraceSpanType {
    /** Complete assistant request. */ AGENT_RUN,
    /** One bounded decision cycle. */ AGENT_ITERATION,
    /** Ollama model invocation. */ LLM_CALL,
    /** Registry-controlled application action. */ TOOL_CALL,
    /** Retrieval exposed through the knowledge tool. */ KNOWLEDGE_SEARCH,
    /** Query vector generation. */ EMBEDDING,
    /** Qdrant similarity query. */ VECTOR_SEARCH,
    /** Persistent-memory read. */ MEMORY_LOOKUP,
    /** Final answer publication stage. */ FINAL_RESPONSE
}

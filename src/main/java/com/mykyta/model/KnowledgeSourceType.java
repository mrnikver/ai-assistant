package com.mykyta.model;

/** Deterministic evidence category attached to every indexed knowledge chunk. */
public enum KnowledgeSourceType {
    SOURCE_CODE,
    DOCUMENTATION,
    RUNBOOK,
    CONFIGURATION,
    TEST,
    MOCK_RUNTIME;

    /** Mock implementation details are never searchable by the Knowledge Agent. */
    public boolean knowledgeSearchable() {
        return this != MOCK_RUNTIME;
    }
}

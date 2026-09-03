package com.mykyta.model;

import java.util.Map;

public record QdrantSearchResult(String text, double score, Map<String, Object> metadata) {
    public QdrantSearchResult(String text, double score) {
        this(text, score, Map.of());
    }

    public QdrantSearchResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

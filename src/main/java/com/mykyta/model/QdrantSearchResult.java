package com.mykyta.model;

public record QdrantSearchResult(
        String text,
        double score
) {
}
package com.mykyta.model;

public record MemoryExtractionResponse(
        boolean shouldStore,
        MemoryKey key,
        String value
) {
}
package com.mykyta.response;

import com.mykyta.model.MemoryKey;

public record MemoryExtractionResponse(
        boolean shouldStore,
        MemoryKey key,
        String value
) {
}

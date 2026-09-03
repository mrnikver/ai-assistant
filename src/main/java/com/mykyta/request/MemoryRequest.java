package com.mykyta.request;

import com.mykyta.model.MemoryKey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemoryRequest(
        @NotNull
        MemoryKey key,

        @NotBlank
        String value
) {
}

package com.mykyta.exception;

import java.time.Instant;

public record ApplicationException(
        String code,
        String message,
        Instant timestamp
) {
}
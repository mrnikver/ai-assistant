package com.mykyta.model;

public record ChatResponse(
        String conversationId,
        String answer,
        Confidence confidence
) {
}
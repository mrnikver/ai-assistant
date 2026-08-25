package com.mykyta.model;

public record  AssistantResponse(
        String answer,
        Confidence confidence
) {}

package com.mykyta.controller.wrapper;

public record ChatRequest(
        String conversationId,
        String message
) {}
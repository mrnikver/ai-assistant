package com.mykyta.controller.wrapper;

import com.mykyta.model.Confidence;

public record ChatResponse(
        String conversationId,
        String answer,
        Confidence confidence
) {
}
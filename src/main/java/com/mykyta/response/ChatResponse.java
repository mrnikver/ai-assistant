package com.mykyta.response;

import com.mykyta.model.Confidence;

public record ChatResponse(
        String conversationId,
        String answer,
        Confidence confidence
) {
}

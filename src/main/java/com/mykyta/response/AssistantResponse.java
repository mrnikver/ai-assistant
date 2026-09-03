package com.mykyta.response;

import com.mykyta.model.Confidence;

public record AssistantResponse(
        String answer,
        Confidence confidence
) {
}

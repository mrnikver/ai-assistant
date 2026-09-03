package com.mykyta.response;

import com.mykyta.model.Confidence;

/**
 * Final structured answer returned after the agent no longer requires tools.
 *
 * @param answer user-facing response grounded in the available conversation and observations
 * @param confidence model-reported confidence in the answer
 */
public record AssistantResponse(
        String answer,
        Confidence confidence
) {
}

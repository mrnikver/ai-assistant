package com.mykyta.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mykyta.model.LLMMessage;

import java.util.List;
import java.util.Map;

/**
 * Represents a request sent to the Ollama chat API.
 *
 * @param model    model to use for inference
 * @param messages conversation context
 * @param stream   whether the response should be streamed
 * @param format   optional structured output schema
 * @param tools    optional tools available to the LLM
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatRequest(
        String model,
        List<LLMMessage> messages,
        boolean stream,
        Map<String, Object> format,
        List<Map<String, Object>> tools
) {
}
package com.mykyta.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a single message exchanged with the LLM chat API.
 *
 * <p>A message can represent:
 * <ul>
 *     <li>a system instruction</li>
 *     <li>a user message</li>
 *     <li>an assistant response</li>
 *     <li>a tool execution result</li>
 * </ul>
 *
 * <p>The {@code role} determines how the LLM should interpret
 * the message inside the conversation context.
 *
 * @param role      author/type of the message, for example
 *                  {@code system}, {@code user}, {@code assistant}, or {@code tool}
 * @param content   textual content of the message; may be {@code null}
 *                  when the assistant returns a tool call instead of text
 * @param toolCalls tool calls requested by the assistant; normally present
 *                  only for messages with role {@code assistant}
 * @param toolName  name of the executed tool; normally present
 *                  only for messages with role {@code tool}
 */
@JsonIgnoreProperties(ignoreUnknown = true) //ignore unknown properties returned from LLM
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LLMMessage(
        String role,
        String content,

        @JsonProperty("tool_calls")
        List<ToolCall> toolCalls,

        @JsonProperty("tool_name")
        String toolName
) {

    public LLMMessage(String role, String content) {
        this(role, content, null, null);
    }
}
package com.mykyta.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.ToolCall;
import com.mykyta.response.AssistantResponse;
import com.mykyta.response.MemoryExtractionResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Produces the single structured input/output summary consumed by logs and traces. */
@Component
public class LlmObservabilitySummarizer {
    private static final Pattern SOURCE = Pattern.compile("path=([^\\s]+)");
    private static final Pattern RESULT = Pattern.compile("(?m)^\\[\\d+]");
    private final LlmObservabilitySanitizer sanitizer;
    private final ObjectMapper objectMapper;

    public LlmObservabilitySummarizer(LlmObservabilitySanitizer sanitizer, ObjectMapper objectMapper) {
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
    }

    public LlmInputSummary input(LlmCallContext call, List<LLMMessage> messages,
                                 List<Map<String, Object>> tools) {
        Map<String, Map<String, Object>> pendingArguments = new LinkedHashMap<>();
        List<ObservationSummary> observations = new ArrayList<>();
        for (LLMMessage message : messages) {
            if (message.toolCalls() != null) {
                for (ToolCall toolCall : message.toolCalls()) {
                    if (toolCall.function() != null && toolCall.function().name() != null) {
                        pendingArguments.put(toolCall.function().name(), sanitizer.map(
                                toolCall.function().arguments() == null ? Map.of() : toolCall.function().arguments()));
                    }
                }
            }
            if ("tool".equals(message.role())) {
                observations.add(observation(message, pendingArguments.getOrDefault(message.toolName(), Map.of())));
            }
        }
        List<String> names = tools.stream().map(this::toolName).toList();
        List<String> agents = names.stream().filter(name -> name.startsWith("ask_"))
                .map(name -> name.contains("knowledge") ? "Knowledge Agent" : "Runtime Agent").toList();
        return new LlmInputSummary(call.purpose().name(), call.agentName(), call.agentType(), call.iteration(),
                sanitizer.preview(call.userRequest()), messages.size(), call.historyMessageCount(), call.memoryCount(),
                names, agents, List.copyOf(observations));
    }

    public LlmOutputSummary toolOrAnswer(LLMMessage message) {
        List<ToolCall> calls = message.toolCalls() == null ? List.of() : message.toolCalls();
        if (!calls.isEmpty()) {
            List<ToolCallSummary> summaries = calls.stream().map(call -> new ToolCallSummary(
                    call.function() == null ? "unknown" : call.function().name(),
                    sanitizer.map(call.function() == null || call.function().arguments() == null
                            ? Map.of() : call.function().arguments()))).toList();
            boolean delegation = summaries.stream().allMatch(call -> call.name().startsWith("ask_"));
            return new LlmOutputSummary(delegation ? LlmOutputType.DELEGATION : LlmOutputType.TOOL_CALL,
                    summaries, null, null, null, Map.of());
        }
        try {
            AssistantResponse response = objectMapper.readValue(message.content(), AssistantResponse.class);
            return new LlmOutputSummary(LlmOutputType.FINAL_RESPONSE, List.of(),
                    sanitizer.preview(response.answer()), response.confidence().name(), null, Map.of());
        } catch (Exception ignored) {
            return new LlmOutputSummary(LlmOutputType.PLAIN_TEXT_FALLBACK, List.of(),
                    sanitizer.preview(message.content()), null, null, Map.of());
        }
    }

    public LlmOutputSummary structuredResult(Object result, Class<?> responseType) {
        Map<String, Object> details = result instanceof MemoryExtractionResponse memory
                ? Map.of("shouldStore", memory.shouldStore(), "key", memory.key() == null ? "none" : memory.key())
                : Map.of("resultType", responseType.getSimpleName());
        return new LlmOutputSummary(LlmOutputType.STRUCTURED_RESULT, List.of(),
                responseType.getSimpleName() + " returned", null, null, details);
    }

    public LlmOutputSummary error(Throwable error) {
        return new LlmOutputSummary(LlmOutputType.ERROR, List.of(), null, null,
                error == null ? "Unknown" : error.getClass().getSimpleName(), Map.of());
    }

    private ObservationSummary observation(LLMMessage message, Map<String, Object> arguments) {
        String content = message.content() == null ? "" : message.content();
        if ("search_knowledge_base".equals(message.toolName())) {
            int count = 0;
            Matcher resultMatcher = RESULT.matcher(content);
            while (resultMatcher.find()) count++;
            List<String> sources = new ArrayList<>();
            Matcher sourceMatcher = SOURCE.matcher(content);
            while (sourceMatcher.find() && sources.size() < 10) sources.add(sourceMatcher.group(1));
            String preview = count == 0 ? "No relevant knowledge chunks"
                    : "Retrieved " + count + " chunks" + (sources.isEmpty() ? "" : " from " + sources);
            return new ObservationSummary(message.toolName(), arguments, sanitizer.preview(preview), count);
        }
        String source = switch (String.valueOf(message.toolName())) {
            case "ask_knowledge_agent" -> "Knowledge Agent";
            case "ask_runtime_agent" -> "Runtime Agent";
            default -> message.toolName();
        };
        return new ObservationSummary(source, arguments, sanitizer.preview(content), null);
    }

    private String toolName(Map<String, Object> definition) {
        Object function = definition.get("function");
        return function instanceof Map<?, ?> map ? String.valueOf(map.get("name")) : "unknown";
    }
}

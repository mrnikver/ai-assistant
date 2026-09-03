package com.mykyta.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.model.Confidence;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.OllamaChatRequest;
import com.mykyta.observability.LlmCallContext;
import com.mykyta.observability.LlmInputSummary;
import com.mykyta.observability.LlmObservabilitySummarizer;
import com.mykyta.observability.LlmOutputSummary;
import com.mykyta.observability.LlmPurpose;
import com.mykyta.response.AssistantResponse;
import com.mykyta.trace.AgentTracer;
import com.mykyta.trace.TraceScope;
import com.mykyta.trace.TraceSpanType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Translates application-level chat operations into Ollama HTTP requests.
 *
 * <p>The client owns transport and response deserialization only. Agent-loop
 * decisions and Java tool execution remain in application services so model
 * output never gains direct access to executable code.</p>
 */
@Slf4j
public class LLMClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final Map<String, Object> structuredOutputSchema;
    private final AgentTracer agentTracer;
    private final LlmObservabilitySummarizer observabilitySummarizer;

    /**
     * Creates an Ollama client using one configured model and final-answer schema.
     *
     * @param baseUrl                Ollama server base URL
     * @param model                  model name used for inference
     * @param objectMapper           JSON serializer
     * @param structuredOutputSchema JSON schema required for final assistant answers
     * @param agentTracer            collector that records safe model-call metadata and duration
     */
    public LLMClient(
            String baseUrl,
            String model,
            ObjectMapper objectMapper,
            Map<String, Object> structuredOutputSchema,
            AgentTracer agentTracer,
            LlmObservabilitySummarizer observabilitySummarizer
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.model = model;
        this.structuredOutputSchema = structuredOutputSchema;
        this.agentTracer = agentTracer;
        this.observabilitySummarizer = observabilitySummarizer;
    }

    /**
     * Requests a structured answer without exposing tools.
     *
     * @param messages complete conversation context
     * @return parsed structured assistant response
     * @throws IOException          if request serialization, transport, or parsing fails
     * @throws InterruptedException if the HTTP operation is interrupted
     */
    public AssistantResponse chat(
            List<LLMMessage> messages
    ) throws IOException, InterruptedException {

        return structuredChat(
                messages,
                structuredOutputSchema,
                AssistantResponse.class,
                new LlmCallContext(LlmPurpose.AGENT_DECISION, "Assistant", "STRUCTURED", null,
                        0, 0, lastUserMessage(messages))
        );
    }

    /**
     * Invokes Ollama with a caller-supplied structured-output schema.
     *
     * @param messages     complete conversation context
     * @param schema       JSON schema Ollama should enforce
     * @param responseType Java response type
     * @param <T>          structured response type
     * @return parsed response content
     * @throws IOException          if request serialization, transport, or parsing fails
     * @throws InterruptedException if the HTTP operation is interrupted
     */
    public <T> T structuredChat(
            List<LLMMessage> messages,
            Map<String, Object> schema,
            Class<T> responseType,
            LlmCallContext callContext
    ) throws IOException, InterruptedException {

        log.debug(
                "Structured LLM request prepared: model={}, responseType={}, messageCount={}",
                model,
                responseType.getSimpleName(),
                messages.size()
        );

        OllamaChatRequest body =
                new OllamaChatRequest(
                        model,
                        messages,
                        false,
                        schema,
                        null
                );

        LlmInputSummary input = observabilitySummarizer.input(callContext, messages, List.of());
        long startedAt = System.nanoTime();
        log.info("LLM call started: traceId={}, agent={}, purpose={}, iteration={}, messages={}, tools={}",
                agentTracer.currentTraceId(), input.agentName(), input.purpose(), input.iteration(),
                input.messageCount(), input.availableTools());
        log.debug("LLM input summary: {}", input);
        try (TraceScope span = llmSpan(callContext, "Structured LLM call")) {
            span.metadata("resultType", responseType.getSimpleName());
            span.metadata("input", input);
            try {
                JsonNode response = send(body);
                String content = response.path("message").path("content").asText();
                T result = objectMapper.readValue(content, responseType);
                LlmOutputSummary output = observabilitySummarizer.structuredResult(result, responseType);
                span.metadata("output", output);
                log.info("LLM call completed: traceId={}, agent={}, purpose={}, iteration={}, output={}, durationMs={}",
                        agentTracer.currentTraceId(), input.agentName(), input.purpose(), input.iteration(),
                        output.type(), elapsedMilliseconds(startedAt));
                log.debug("LLM output summary: {}", output);
                return result;
            } catch (IOException | InterruptedException | RuntimeException exception) {
                LlmOutputSummary output = observabilitySummarizer.error(exception);
                span.metadata("output", output);
                span.fail(exception);
                log.info("LLM call failed: traceId={}, agent={}, purpose={}, iteration={}, error={}, durationMs={}",
                        agentTracer.currentTraceId(), input.agentName(), input.purpose(), input.iteration(),
                        output.errorType(), elapsedMilliseconds(startedAt));
                throw exception;
            }
        }
    }

    /**
     * Requests one agent decision with the registry's available tools.
     *
     * <p>The response either contains tool calls or final content. Tool-enabled
     * requests intentionally omit Ollama's {@code format} option because some
     * model/runtime combinations prioritize structured output and stop emitting
     * tool calls when both features are requested together.</p>
     *
     * @param messages current agent conversation including prior observations
     * @param tools    registered tool definitions in Ollama format
     * @return assistant message containing requested actions or final JSON content
     * @throws IOException          if request serialization, transport, or parsing fails
     * @throws InterruptedException if the HTTP operation is interrupted
     */
    public LLMMessage chatWithTools(
            List<LLMMessage> messages,
            List<Map<String, Object>> tools,
            LlmCallContext callContext
    ) throws IOException, InterruptedException {

        log.debug(
                "Tool-enabled LLM request prepared: model={}, messageCount={}, toolCount={}",
                model,
                messages.size(),
                tools.size()
        );

        OllamaChatRequest body = new OllamaChatRequest(
                model,
                messages,
                false,
                null,
                tools
        );

        LlmInputSummary input = observabilitySummarizer.input(callContext, messages, tools);
        long startedAt = System.nanoTime();
        log.info("LLM call started: traceId={}, agent={}, purpose={}, iteration={}, messages={}, tools={}",
                agentTracer.currentTraceId(), input.agentName(), input.purpose(), input.iteration(),
                input.messageCount(), input.availableTools());
        log.debug("LLM input summary: {}", input);
        try (TraceScope span = llmSpan(callContext, "Agent LLM decision")) {
            span.metadata("input", input);
            try {
                JsonNode response = send(body);
                LLMMessage message = objectMapper.treeToValue(response.path("message"), LLMMessage.class);
                List<String> toolNames = message.toolCalls() == null ? List.of() : message.toolCalls().stream()
                        .map(call -> call.function() == null ? "unknown" : call.function().name()).toList();
                span.metadata("resultType", toolNames.isEmpty() ? "FINAL_RESPONSE" : "TOOL_CALLS");
                span.metadata("toolNames", toolNames);
                LlmOutputSummary output = observabilitySummarizer.toolOrAnswer(message);
                span.metadata("output", output);
                log.info("LLM call completed: traceId={}, agent={}, purpose={}, iteration={}, output={}, tools={}, durationMs={}",
                        agentTracer.currentTraceId(), input.agentName(), input.purpose(), input.iteration(),
                        output.type(), toolNames, elapsedMilliseconds(startedAt));
                log.debug("LLM output summary: {}", output);
                return message;
            } catch (IOException | InterruptedException | RuntimeException exception) {
                LlmOutputSummary output = observabilitySummarizer.error(exception);
                span.metadata("output", output);
                span.fail(exception);
                log.info("LLM call failed: traceId={}, agent={}, purpose={}, iteration={}, error={}, durationMs={}",
                        agentTracer.currentTraceId(), input.agentName(), input.purpose(), input.iteration(),
                        output.errorType(), elapsedMilliseconds(startedAt));
                throw exception;
            }
        }
    }

    /**
     * Parses the final content from a tool-enabled turn into the public response model.
     *
     * <p>The system prompt asks for the normal JSON response shape. If a local
     * model returns plain text instead, the text is preserved as the answer with
     * medium confidence so a formatting imperfection does not fail the request.</p>
     *
     * @param message assistant message that contains no tool calls
     * @return structured final answer, including a safe plain-text fallback
     * @throws IOException if the model content is absent
     */
    public AssistantResponse parseAssistantResponse(LLMMessage message) throws IOException {
        if (message == null || message.content() == null || message.content().isBlank()) {
            throw new IOException("LLM returned neither tool calls nor final answer content");
        }
        try {
            return objectMapper.readValue(message.content(), AssistantResponse.class);
        } catch (JsonProcessingException exception) {
            log.warn("LLM final answer did not match the structured response schema; using plain-text fallback");
            return new AssistantResponse(message.content(), Confidence.MEDIUM);
        }
    }

    private JsonNode send(
            OllamaChatRequest body
    ) throws IOException, InterruptedException {

        long startedAt = System.nanoTime();

        String json =
                objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {
            log.error(
                    "LLM request failed: model={}, status={}, durationMs={}",
                    model,
                    response.statusCode(),
                    elapsedMilliseconds(startedAt)
            );
            throw new RuntimeException(
                    "LLM request failed: "
                            + response.statusCode()
                            + " "
                            + response.body()
            );
        }

        log.debug(
                "LLM request completed: model={}, status={}, durationMs={}",
                model,
                response.statusCode(),
                elapsedMilliseconds(startedAt)
        );

        return objectMapper.readTree(
                response.body()
        );
    }

    private TraceScope llmSpan(LlmCallContext context, String name) {
        TraceScope span = agentTracer.startSpan(TraceSpanType.LLM_CALL, name);
        span.metadata("callNumber", agentTracer.nextLlmCallNumber());
        span.metadata("model", model);
        span.metadata("iteration", agentTracer.currentIteration());
        span.metadata("agentName", context.agentName());
        span.metadata("agentType", context.agentType());
        span.metadata("purpose", context.purpose().name());
        return span;
    }

    private static String lastUserMessage(List<LLMMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if ("user".equals(messages.get(index).role())) return messages.get(index).content();
        }
        return null;
    }

    private long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

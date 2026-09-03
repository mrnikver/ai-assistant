package com.mykyta.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.model.Confidence;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.OllamaChatRequest;
import com.mykyta.response.AssistantResponse;
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

    /**
     * Creates an Ollama client using one configured model and final-answer schema.
     *
     * @param baseUrl Ollama server base URL
     * @param model model name used for inference
     * @param objectMapper JSON serializer
     * @param structuredOutputSchema JSON schema required for final assistant answers
     */
    public LLMClient(
            String baseUrl,
            String model,
            ObjectMapper objectMapper,
            Map<String, Object> structuredOutputSchema
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.model = model;
        this.structuredOutputSchema = structuredOutputSchema;
    }

    /**
     * Requests a structured answer without exposing tools.
     *
     * @param messages complete conversation context
     * @return parsed structured assistant response
     * @throws IOException if request serialization, transport, or parsing fails
     * @throws InterruptedException if the HTTP operation is interrupted
     */
    public AssistantResponse chat(
            List<LLMMessage> messages
    ) throws IOException, InterruptedException {

        return structuredChat(
                messages,
                structuredOutputSchema,
                AssistantResponse.class
        );
    }

    /**
     * Invokes Ollama with a caller-supplied structured-output schema.
     *
     * @param messages complete conversation context
     * @param schema JSON schema Ollama should enforce
     * @param responseType Java response type
     * @param <T> structured response type
     * @return parsed response content
     * @throws IOException if request serialization, transport, or parsing fails
     * @throws InterruptedException if the HTTP operation is interrupted
     */
    public <T> T structuredChat(
            List<LLMMessage> messages,
            Map<String, Object> schema,
            Class<T> responseType
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

        JsonNode response = send(body);

        String content = response
                .path("message")
                .path("content")
                .asText();

        return objectMapper.readValue(
                content,
                responseType
        );
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
     * @param tools registered tool definitions in Ollama format
     * @return assistant message containing requested actions or final JSON content
     * @throws IOException if request serialization, transport, or parsing fails
     * @throws InterruptedException if the HTTP operation is interrupted
     */
    public LLMMessage chatWithTools(
            List<LLMMessage> messages,
            List<Map<String, Object>> tools
    ) throws IOException, InterruptedException {

        log.debug(
                "Tool-enabled LLM request prepared: model={}, messageCount={}, toolCount={}",
                model,
                messages.size(),
                tools.size()
        );

        OllamaChatRequest body =
                new OllamaChatRequest(
                        model,
                        messages,
                        false,
                        null,
                        tools
                );

        JsonNode response = send(body);

        return objectMapper.treeToValue(
                response.path("message"),
                LLMMessage.class
        );
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

    private long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

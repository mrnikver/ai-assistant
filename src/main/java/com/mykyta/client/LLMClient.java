package com.mykyta.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.OllamaChatRequest;
import com.mykyta.response.AssistantResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class LLMClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final Map<String, Object> structuredOutputSchema;

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

    public AssistantResponse chat(
            List<LLMMessage> messages
    ) throws IOException, InterruptedException {

        return structuredChat(
                messages,
                structuredOutputSchema,
                AssistantResponse.class
        );
    }

    public <T> T structuredChat(
            List<LLMMessage> messages,
            Map<String, Object> schema,
            Class<T> responseType
    ) throws IOException, InterruptedException {

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

    public LLMMessage chatWithTools(
            List<LLMMessage> messages,
            List<Map<String, Object>> tools
    ) throws IOException, InterruptedException {

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

    private JsonNode send(
            OllamaChatRequest body
    ) throws IOException, InterruptedException {

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
            throw new RuntimeException(
                    "LLM request failed: "
                            + response.statusCode()
                            + " "
                            + response.body()
            );
        }

        return objectMapper.readTree(
                response.body()
        );
    }
}

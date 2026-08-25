package com.mykyta.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.model.AssistantResponse;
import com.mykyta.model.LLMMessage;

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

    public LLMClient(String baseUrl, String model) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public AssistantResponse chat(List<LLMMessage> messages) throws IOException, InterruptedException {

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "stream", false,
                "format", getStructuredOutput()
        );

        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(
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

        JsonNode jsonResponse = objectMapper.readTree(response.body());

        String content = jsonResponse
                .get("message")
                .get("content")
                .asText();

        return objectMapper.readValue(
                content,
                AssistantResponse.class
        );
    }

    public LLMMessage chatWithTools(
            List<LLMMessage> messages,
            List<Map<String, Object>> tools
    ) throws IOException, InterruptedException {

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "tools", tools,
                "stream", false
        );

        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(
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

        JsonNode jsonResponse =
                objectMapper.readTree(response.body());

        JsonNode message =
                jsonResponse.path("message");

        return objectMapper.treeToValue(
                message,
                LLMMessage.class
        );
    }

    private Map<String, Object> getStructuredOutput() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "answer", Map.of(
                                "type", "string"
                        ),
                        "confidence", Map.of(
                                "type", "string",
                                "enum", List.of("LOW", "MEDIUM", "HIGH")
                        )
                ),
                "required", List.of(
                        "answer",
                        "confidence"
                )
        );
    }
}

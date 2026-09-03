package com.mykyta.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.config.EmbeddingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Component
@Slf4j
public class EmbeddingClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingProperties properties;

    public EmbeddingClient(
            ObjectMapper objectMapper,
            EmbeddingProperties properties
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public double[] embed(String text)
            throws IOException, InterruptedException {

        long startedAt = System.nanoTime();
        log.debug("Embedding request started: model={}, inputLength={}", properties.model(), text.length());

        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "input", text
        );

        String json =
                objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(
                        URI.create(
                                properties.baseUrl() + "/api/embed"
                        )
                )
                .header("Content-Type", "application/json")
                .POST(
                        HttpRequest.BodyPublishers.ofString(json)
                )
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {
            log.error(
                    "Embedding request failed: model={}, status={}, durationMs={}",
                    properties.model(),
                    response.statusCode(),
                    elapsedMilliseconds(startedAt)
            );
            throw new RuntimeException(
                    "Embedding request failed: "
                            + response.statusCode()
                            + " "
                            + response.body()
            );
        }

        JsonNode embeddings =
                objectMapper.readTree(response.body())
                        .path("embeddings")
                        .get(0);

        double[] vector = objectMapper.treeToValue(
                embeddings,
                double[].class
        );

        log.debug(
                "Embedding request completed: model={}, dimensions={}, durationMs={}",
                properties.model(),
                vector.length,
                elapsedMilliseconds(startedAt)
        );
        return vector;
    }

    private long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

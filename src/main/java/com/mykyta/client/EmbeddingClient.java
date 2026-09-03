package com.mykyta.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.config.EmbeddingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.mykyta.trace.AgentTracer;
import com.mykyta.trace.TraceScope;
import com.mykyta.trace.TraceSpanType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** Calls the configured embedding service while exposing only safe operational trace metadata. */
@Component
@Slf4j
public class EmbeddingClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingProperties properties;
    private final AgentTracer agentTracer;

    /**
     * Creates the embedding transport used by indexing and request-time retrieval.
     * @param objectMapper JSON serializer
     * @param properties embedding endpoint and model configuration
     * @param agentTracer request trace collector; inactive during startup indexing
     */
    public EmbeddingClient(
            ObjectMapper objectMapper,
            EmbeddingProperties properties,
            AgentTracer agentTracer
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.agentTracer = agentTracer;
    }

    /**
     * Generates one vector and records an embedding child span when a chat trace is active.
     * @param text text to embed; its content is never written to the trace
     * @return model-generated vector
     * @throws IOException when transport or response parsing fails
     * @throws InterruptedException when the HTTP request is interrupted
     */
    public double[] embed(String text)
            throws IOException, InterruptedException {

        try (TraceScope span = agentTracer.startSpan(TraceSpanType.EMBEDDING, "Generate query embedding")) {
            span.metadata("model", properties.model());
            span.metadata("inputLength", text.length());
            try {
                double[] vector = doEmbed(text);
                span.metadata("dimensions", vector.length);
                return vector;
            } catch (IOException | InterruptedException | RuntimeException exception) {
                span.fail(exception);
                throw exception;
            }
        }
    }

    private double[] doEmbed(String text) throws IOException, InterruptedException {

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

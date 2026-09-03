package com.mykyta.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.config.QdrantProperties;
import com.mykyta.model.QdrantPoint;
import com.mykyta.model.QdrantSearchResult;
import com.mykyta.model.QdrantUpsertRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class VectorStoreClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final QdrantProperties properties;

    public VectorStoreClient(
            ObjectMapper objectMapper,
            QdrantProperties properties
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void ensureCollection(int vectorSize) throws IOException, InterruptedException {
        URI collectionUri = collectionUri();
        HttpRequest getRequest = HttpRequest.newBuilder(collectionUri)
                .GET()
                .build();

        HttpResponse<String> getResponse = httpClient.send(
                getRequest,
                HttpResponse.BodyHandlers.ofString()
        );

        if (getResponse.statusCode() == 200) {
            return;
        }

        if (getResponse.statusCode() != 404) {
            throw new RuntimeException(
                    "Qdrant collection lookup failed: "
                            + getResponse.statusCode()
                            + " "
                            + getResponse.body()
            );
        }

        Map<String, Object> body = Map.of(
                "vectors", Map.of(
                        "size", vectorSize,
                        "distance", "Cosine"
                )
        );

        HttpRequest createRequest = HttpRequest.newBuilder(collectionUri)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> createResponse = httpClient.send(
                createRequest,
                HttpResponse.BodyHandlers.ofString()
        );

        if (createResponse.statusCode() != 200) {
            throw new RuntimeException(
                    "Qdrant collection creation failed: "
                            + createResponse.statusCode()
                            + " "
                            + createResponse.body()
            );
        }
    }

    public void upsert(
            String text,
            double[] vector
    ) throws IOException, InterruptedException {

        QdrantPoint point =
                new QdrantPoint(
                        UUID.nameUUIDFromBytes(
                                text.getBytes(StandardCharsets.UTF_8)
                        ).toString(),
                        vector,
                        Map.of("text", text)
                );

        QdrantUpsertRequest body =
                new QdrantUpsertRequest(
                        List.of(point)
                );

        String json =
                objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(
                        URI.create(
                                properties.baseUrl()
                                        + "/collections/"
                                        + properties.collection()
                                        + "/points?wait=true"
                        )
                )
                .header("Content-Type", "application/json")
                .PUT(
                        HttpRequest.BodyPublishers.ofString(json)
                )
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Qdrant upsert failed: "
                            + response.statusCode()
                            + " "
                            + response.body()
            );
        }
    }

    public List<QdrantSearchResult> search(
            double[] queryVector,
            int limit
    ) throws IOException, InterruptedException {

        Map<String, Object> body = Map.of(
                "query", queryVector,
                "limit", limit,
                "with_payload", true
        );

        String json =
                objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(
                        URI.create(
                                properties.baseUrl()
                                        + "/collections/"
                                        + properties.collection()
                                        + "/points/query"
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
            throw new RuntimeException(
                    "Qdrant search failed: "
                            + response.statusCode()
                            + " "
                            + response.body()
            );
        }

        JsonNode points =
                objectMapper.readTree(response.body())
                        .path("result")
                        .path("points");

        List<QdrantSearchResult> results =
                new ArrayList<>();

        for (JsonNode point : points) {

            results.add(
                    new QdrantSearchResult(
                            point.path("payload")
                                    .path("text")
                                    .asText(),
                            point.path("score")
                                    .asDouble()
                    )
            );
        }

        return results;
    }

    private URI collectionUri() {
        return URI.create(
                properties.baseUrl()
                        + "/collections/"
                        + properties.collection()
        );
    }
}

package com.mykyta.rag;

import com.mykyta.client.EmbeddingClient;
import com.mykyta.client.VectorStoreClient;
import com.mykyta.model.QdrantSearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RunbookRetriever implements KnowledgeRetriever {

    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;

    public RunbookRetriever(
            EmbeddingClient embeddingClient,
            VectorStoreClient vectorStoreClient
    ) {
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
    }

    @Override
    public String retrieve(String query) {

        try {
            double[] queryEmbedding =
                    embeddingClient.embed(query);

            List<QdrantSearchResult> results =
                    vectorStoreClient.search(
                            queryEmbedding,
                            1
                    );

            if (results.isEmpty()) {
                return "";
            }

            QdrantSearchResult result = results.get(0);

            System.out.println(
                    "Retrieved score: "
                            + result.score()
            );

            return result.text();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to retrieve deployment knowledge",
                    e
            );
        }
    }
}
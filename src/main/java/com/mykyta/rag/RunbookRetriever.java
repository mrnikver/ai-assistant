package com.mykyta.rag;

import com.mykyta.client.EmbeddingClient;
import com.mykyta.client.VectorStoreClient;
import com.mykyta.model.QdrantSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
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

        long startedAt = System.nanoTime();
        log.debug("Runbook retrieval started: queryLength={}", query.length());

        try {
            double[] queryEmbedding =
                    embeddingClient.embed(query);

            List<QdrantSearchResult> results =
                    vectorStoreClient.search(
                            queryEmbedding,
                            1
                    );

            if (results.isEmpty()) {
                log.info(
                        "Runbook retrieval completed without a match: durationMs={}",
                        (System.nanoTime() - startedAt) / 1_000_000
                );
                return "";
            }

            QdrantSearchResult result = results.get(0);
            log.info(
                    "Runbook retrieval completed: score={}, resultLength={}, durationMs={}",
                    result.score(),
                    result.text().length(),
                    (System.nanoTime() - startedAt) / 1_000_000
            );

            return result.text();

        } catch (Exception e) {
            log.error(
                    "Runbook retrieval failed: durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    e
            );
            throw new RuntimeException(
                    "Failed to retrieve deployment knowledge",
                    e
            );
        }
    }
}

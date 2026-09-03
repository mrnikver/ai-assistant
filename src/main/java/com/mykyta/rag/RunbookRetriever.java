package com.mykyta.rag;

import com.mykyta.client.EmbeddingClient;
import com.mykyta.client.VectorStoreClient;
import com.mykyta.model.QdrantSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implements semantic knowledge retrieval using the existing embedding and Qdrant clients.
 *
 * <p>The retriever deliberately knows nothing about agent decisions or final
 * answer generation. It embeds the supplied query and returns the requested
 * number of vector-search matches.</p>
 */
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
    public List<QdrantSearchResult> retrieve(String query, int limit) {

        long startedAt = System.nanoTime();
        log.debug("Runbook retrieval started: queryLength={}", query.length());

        try {
            double[] queryEmbedding =
                    embeddingClient.embed(query);

            List<QdrantSearchResult> results =
                    vectorStoreClient.search(
                            queryEmbedding,
                            limit
                    );

            if (results.isEmpty()) {
                log.info(
                        "Runbook retrieval completed without a match: durationMs={}",
                        (System.nanoTime() - startedAt) / 1_000_000
                );
                return List.of();
            }

            log.info(
                    "Runbook retrieval completed: resultCount={}, durationMs={}",
                    results.size(),
                    (System.nanoTime() - startedAt) / 1_000_000
            );

            return results;

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

package com.mykyta.rag;

import com.mykyta.client.EmbeddingClient;
import com.mykyta.client.VectorStoreClient;
import com.mykyta.model.QdrantSearchResult;
import com.mykyta.model.KnowledgeSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

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
    public List<QdrantSearchResult> retrieve(String query, int limit, Set<KnowledgeSourceType> sourceTypes) {

        long startedAt = System.nanoTime();
        log.debug("Knowledge retrieval started: queryLength={}, sourceTypes={}", query.length(), sourceTypes);

        try {
            double[] queryEmbedding =
                    embeddingClient.embed(query);

            List<QdrantSearchResult> results =
                    vectorStoreClient.search(
                            queryEmbedding,
                            limit,
                            sourceTypes
                    );

            if (results.isEmpty()) {
                log.info(
                        "Knowledge retrieval completed without a match: sourceTypes={}, durationMs={}",
                        sourceTypes,
                        (System.nanoTime() - startedAt) / 1_000_000
                );
                return List.of();
            }

            log.info(
                    "Knowledge retrieval completed: resultCount={}, sourceTypes={}, durationMs={}",
                    results.size(),
                    sourceTypes,
                    (System.nanoTime() - startedAt) / 1_000_000
            );

            return results;

        } catch (Exception e) {
            log.error(
                    "Knowledge retrieval failed: sourceTypes={}, durationMs={}",
                    sourceTypes,
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

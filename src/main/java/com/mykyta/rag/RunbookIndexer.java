package com.mykyta.rag;

import com.mykyta.client.EmbeddingClient;
import com.mykyta.client.VectorStoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class RunbookIndexer implements ApplicationRunner {

    private final TextChunker textChunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;

    public RunbookIndexer(
            TextChunker textChunker,
            EmbeddingClient embeddingClient,
            VectorStoreClient vectorStoreClient
    ) {
        this.textChunker = textChunker;
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        long startedAt = System.nanoTime();
        log.info("Runbook indexing started");

        ClassPathResource resource =
                new ClassPathResource(
                        "knowledge/deployment-runbook.txt"
                );

        String document =
                resource.getContentAsString(
                        StandardCharsets.UTF_8
                );

        List<String> chunks =
                textChunker.chunk(document);
        log.info("Runbook chunking completed: chunkCount={}", chunks.size());

        boolean collectionInitialized = false;

        for (String chunk : chunks) {

            double[] embedding =
                    embeddingClient.embed(chunk);

            if (!collectionInitialized) {
                vectorStoreClient.ensureCollection(embedding.length);
                collectionInitialized = true;
            }

            vectorStoreClient.upsert(
                    chunk,
                    embedding
            );
        }
        log.info(
                "Runbook indexing completed: chunkCount={}, durationMs={}",
                chunks.size(),
                (System.nanoTime() - startedAt) / 1_000_000
        );
    }
}

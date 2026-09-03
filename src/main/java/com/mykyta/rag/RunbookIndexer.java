package com.mykyta.rag;

import com.mykyta.client.EmbeddingClient;
import com.mykyta.client.VectorStoreClient;
import com.mykyta.model.KnowledgeChunk;
import com.mykyta.model.KnowledgeSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Indexes the small bundled operational corpus with explicit RUNBOOK metadata. */
@Component
@Slf4j
public class RunbookIndexer implements ApplicationRunner {
    private static final List<String> RESOURCES = List.of(
            "knowledge/deployment-runbook.txt",
            "knowledge/runbooks/deployment-failure.md",
            "knowledge/runbooks/database-connectivity.md",
            "knowledge/runbooks/service-unhealthy.md",
            "knowledge/runbooks/missing-logs.md",
            "knowledge/runbooks/deployment-rollback.md"
    );

    private final TextChunker textChunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;

    public RunbookIndexer(TextChunker textChunker, EmbeddingClient embeddingClient,
                          VectorStoreClient vectorStoreClient) {
        this.textChunker = textChunker;
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        long startedAt = System.nanoTime();
        String indexRunId = UUID.randomUUID().toString();
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (String resourcePath : RESOURCES) {
            String document = new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
            List<String> documentChunks = resourcePath.endsWith(".md")
                    ? List.of(document.trim()) : textChunker.chunk(document);
            for (int index = 0; index < documentChunks.size(); index++) {
                String text = documentChunks.get(index);
                chunks.add(new KnowledgeChunk(text, "ai-assist", resourcePath, KnowledgeSourceType.RUNBOOK,
                        resourcePath.endsWith(".md") ? "markdown" : "text", heading(text, resourcePath),
                        1, (int) Math.min(Integer.MAX_VALUE, text.lines().count()), index));
            }
        }

        boolean collectionInitialized = false;
        for (KnowledgeChunk chunk : chunks) {
            double[] embedding = embeddingClient.embed("Source type: RUNBOOK\nPath: " + chunk.sourcePath()
                    + "\nHeading: " + chunk.context() + "\n\n" + chunk.text());
            if (!collectionInitialized) {
                vectorStoreClient.ensureCollection(embedding.length);
                collectionInitialized = true;
            }
            vectorStoreClient.upsertRunbook(chunk, embedding, indexRunId);
        }
        vectorStoreClient.deleteRunbookChunksExcept(indexRunId);
        log.info("Operational runbook indexing completed: documentCount={}, chunkCount={}, durationMs={}",
                RESOURCES.size(), chunks.size(), (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String heading(String text, String resourcePath) {
        return text.lines().map(String::trim).filter(line -> line.startsWith("#"))
                .map(line -> line.replaceFirst("^#+\\s*", "")).findFirst()
                .orElseGet(() -> resourcePath.substring(resourcePath.lastIndexOf('/') + 1));
    }
}

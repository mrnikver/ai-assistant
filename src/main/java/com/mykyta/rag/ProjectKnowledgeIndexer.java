package com.mykyta.rag;

import com.mykyta.client.EmbeddingClient;
import com.mykyta.client.VectorStoreClient;
import com.mykyta.config.ProjectKnowledgeProperties;
import com.mykyta.model.KnowledgeChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Recursively indexes the backend and UI source trees into the configured knowledge collection. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class ProjectKnowledgeIndexer implements ApplicationRunner {

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".idea", ".gradle", ".next", "node_modules", "target", "build", "dist", "coverage");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("java", "md", "markdown", "ts", "tsx");

    private final ProjectKnowledgeProperties properties;
    private final ProjectDocumentChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;

    public ProjectKnowledgeIndexer(ProjectKnowledgeProperties properties, ProjectDocumentChunker chunker,
                                   EmbeddingClient embeddingClient, VectorStoreClient vectorStoreClient) {
        this.properties = properties;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.enabled()) {
            log.info("Project knowledge indexing is disabled");
            return;
        }

        long startedAt = System.nanoTime();
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int fileCount = 0;
        for (String configuredRoot : properties.roots()) {
            Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                log.warn("Skipping missing project knowledge root: root={}", root);
                continue;
            }
            List<Path> files = sourceFiles(root);
            fileCount += files.size();
            for (Path file : files) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                chunks.addAll(chunker.chunk(root, file, content, properties.maxChunkCharacters()));
            }
        }

        if (chunks.isEmpty()) {
            log.warn("Project knowledge indexing found no supported source documents");
            return;
        }

        String indexRunId = UUID.randomUUID().toString();
        boolean collectionInitialized = false;
        for (KnowledgeChunk chunk : chunks) {
            String embeddingText = embeddingText(chunk);
            double[] embedding = embeddingClient.embed(embeddingText);
            if (!collectionInitialized) {
                vectorStoreClient.ensureCollection(embedding.length);
                collectionInitialized = true;
            }
            vectorStoreClient.upsert(chunk, embedding, indexRunId);
        }
        vectorStoreClient.deleteProjectChunksExcept(indexRunId);
        log.info("Project knowledge indexing completed: rootCount={}, fileCount={}, chunkCount={}, durationMs={}",
                properties.roots().size(), fileCount, chunks.size(), (System.nanoTime() - startedAt) / 1_000_000);
    }

    private List<Path> sourceFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.startsWith(root))
                    .filter(path -> !isExcluded(root, path))
                    .filter(this::isSupported)
                    .sorted()
                    .toList();
        }
    }

    private boolean isExcluded(Path root, Path path) {
        for (Path part : root.relativize(path)) {
            if (EXCLUDED_DIRECTORIES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupported(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SUPPORTED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
    }

    private String embeddingText(KnowledgeChunk chunk) {
        return "Project: " + chunk.project() + "\nPath: " + chunk.sourcePath() + "\nContext: "
                + chunk.context() + "\n\n" + chunk.text();
    }
}

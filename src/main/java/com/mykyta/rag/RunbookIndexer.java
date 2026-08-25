package com.mykyta.rag;

import com.mykyta.client.EmbeddingClient;
import com.mykyta.client.VectorStoreClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
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

        for (String chunk : chunks) {

            double[] embedding =
                    embeddingClient.embed(chunk);

            vectorStoreClient.upsert(
                    chunk,
                    embedding
            );
        }
        //TODO: index documents only when documents change
        System.out.println(
                "Indexed " + chunks.size() + " runbook chunks"
        );
    }
}
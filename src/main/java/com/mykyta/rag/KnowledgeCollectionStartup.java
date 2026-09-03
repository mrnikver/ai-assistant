package com.mykyta.rag;

import com.mykyta.client.VectorStoreClient;
import com.mykyta.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Applies the optional destructive local-development reset before either indexer runs. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class KnowledgeCollectionStartup implements ApplicationRunner {
    private final RagProperties properties;
    private final VectorStoreClient vectorStoreClient;

    public KnowledgeCollectionStartup(RagProperties properties, VectorStoreClient vectorStoreClient) {
        this.properties = properties;
        this.vectorStoreClient = vectorStoreClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.resetOnStartup()) {
            log.info("Knowledge collection reset skipped: rag.reset-on-startup=false; incremental indexing retained");
            return;
        }
        log.warn("Knowledge collection reset started: rag.reset-on-startup=true");
        vectorStoreClient.deleteCollectionIfExists();
        log.info("Knowledge collection reset completed; the runbook indexer will recreate it before the first upsert");
    }
}

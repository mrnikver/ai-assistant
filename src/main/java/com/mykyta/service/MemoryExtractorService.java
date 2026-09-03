package com.mykyta.service;

import com.mykyta.client.LLMClient;
import com.mykyta.model.LLMMessage;
import com.mykyta.response.MemoryExtractionResponse;
import com.mykyta.util.JsonResourceLoader;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MemoryExtractorService {

    private static final String SYSTEM_PROMPT = """
            Determine whether the user message contains a durable fact
            that should be stored as persistent application memory.

            Store stable facts such as:
            - production region
            - default service
            - persistent configuration
            - long-term preferences

            Do not store:
            - questions
            - temporary runtime status
            - deployment logs
            - one-time errors
            - speculative information

            If memory should be stored:
            - shouldStore = true
            - use a concise snake_case key
            - extract the corresponding value

            Otherwise:
            - shouldStore = false
            - key = null
            - value = null
            """;

    private final LLMClient llmClient;
    private final Map<String, Object> schema;

    public MemoryExtractorService(
            LLMClient llmClient,
            JsonResourceLoader jsonResourceLoader
    ) throws Exception {
        this.llmClient = llmClient;
        this.schema = jsonResourceLoader.load(
                "schemas/memory-extraction.json"
        );
    }

    public MemoryExtractionResponse extract(
            String userMessage
    ) throws Exception {

        List<LLMMessage> messages = List.of(
                new LLMMessage(
                        "system",
                        SYSTEM_PROMPT
                ),
                new LLMMessage(
                        "user",
                        userMessage
                )
        );

        return llmClient.structuredChat(
                messages,
                schema,
                MemoryExtractionResponse.class
        );
    }
}

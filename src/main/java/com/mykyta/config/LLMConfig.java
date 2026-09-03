package com.mykyta.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.client.LLMClient;
import com.mykyta.util.JsonResourceLoader;
import com.mykyta.trace.AgentTracer;
import com.mykyta.observability.LlmObservabilitySummarizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Map;

/** Wires the configured Ollama client with schemas and execution tracing. */
@Configuration
public class LLMConfig {

    /**
     * Creates the shared model transport used by memory extraction and the agent loop.
     * @param properties Ollama endpoint and model
     * @param objectMapper JSON serializer
     * @param jsonResourceLoader schema loader
     * @param agentTracer safe request-local trace collector
     * @return configured LLM client
     * @throws IOException when the assistant response schema cannot be loaded
     */
    @Bean
    public LLMClient llmClient(LLMProperties properties,
                               ObjectMapper objectMapper,
                               JsonResourceLoader jsonResourceLoader,
                               AgentTracer agentTracer,
                               LlmObservabilitySummarizer observabilitySummarizer) throws IOException {
        Map<String, Object> schema = jsonResourceLoader.load("schemas/assistant-response.json");

        return new LLMClient(
                properties.baseUrl(),
                properties.model(),
                objectMapper,
                schema,
                agentTracer,
                observabilitySummarizer
        );
    }
}

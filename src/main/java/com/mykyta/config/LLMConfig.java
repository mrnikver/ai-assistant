package com.mykyta.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.client.LLMClient;
import com.mykyta.util.JsonResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Map;

@Configuration
public class LLMConfig {
    @Bean
    public LLMClient llmClient(LLMProperties properties,
                               ObjectMapper objectMapper,
                               JsonResourceLoader jsonResourceLoader) throws IOException {
        Map<String, Object> schema =
                jsonResourceLoader.load(
                        "schemas/assistant-response.json"
                );

        return new LLMClient(
                properties.baseUrl(),
                properties.model(),
                objectMapper,
                schema
        );
    }
}

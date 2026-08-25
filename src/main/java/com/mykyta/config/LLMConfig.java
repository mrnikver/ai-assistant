package com.mykyta.config;

import com.mykyta.client.LLMClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMConfig {
    @Bean
    public LLMClient llmClient(LLMProperties properties) {
        return new LLMClient(
                properties.baseUrl(),
                properties.model()
        );
    }
}

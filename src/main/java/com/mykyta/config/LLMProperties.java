package com.mykyta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LLMProperties(
        String baseUrl,
        String model
) {
}
package com.mykyta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "project-knowledge")
public record ProjectKnowledgeProperties(
        boolean enabled,
        List<String> roots,
        int maxChunkCharacters
) {
    public ProjectKnowledgeProperties {
        roots = roots == null ? List.of() : List.copyOf(roots);
        if (maxChunkCharacters <= 0) {
            maxChunkCharacters = 2400;
        }
    }
}

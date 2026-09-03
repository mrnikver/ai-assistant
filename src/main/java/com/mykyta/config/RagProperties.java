package com.mykyta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Controls knowledge-corpus startup lifecycle behavior. */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        @DefaultValue("false") boolean resetOnStartup
) { }

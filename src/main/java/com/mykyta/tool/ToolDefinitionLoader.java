package com.mykyta.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
public class ToolDefinitionLoader {

    private final ObjectMapper objectMapper;

    public ToolDefinitionLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> load(String fileName) throws IOException {
        try (InputStream inputStream =
                     getClass()
                             .getClassLoader()
                             .getResourceAsStream("tools/" + fileName)) {

            if (inputStream == null) {
                throw new IllegalArgumentException(
                        "Tool definition not found: " + fileName
                );
            }

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {}
            );
        }
    }
}
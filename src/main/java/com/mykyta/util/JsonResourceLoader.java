package com.mykyta.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
public class JsonResourceLoader {

    private final ObjectMapper objectMapper;

    public JsonResourceLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> load(String resourcePath) throws IOException {

        try (InputStream inputStream =
                     getClass()
                             .getClassLoader()
                             .getResourceAsStream(resourcePath)) {

            if (inputStream == null) {
                throw new IllegalArgumentException(
                        "Resource not found: " + resourcePath
                );
            }

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {}
            );
        }
    }
}
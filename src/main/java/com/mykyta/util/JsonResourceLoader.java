package com.mykyta.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
@Slf4j
public class JsonResourceLoader {

    private final ObjectMapper objectMapper;

    public JsonResourceLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> load(String resourcePath) throws IOException {

        log.debug("Loading JSON classpath resource: path={}", resourcePath);

        try (InputStream inputStream =
                     getClass()
                             .getClassLoader()
                             .getResourceAsStream(resourcePath)) {

            if (inputStream == null) {
                log.error("JSON classpath resource not found: path={}", resourcePath);
                throw new IllegalArgumentException(
                        "Resource not found: " + resourcePath
                );
            }

            Map<String, Object> resource = objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {}
            );
            log.debug("JSON classpath resource loaded: path={}", resourcePath);
            return resource;
        }
    }
}

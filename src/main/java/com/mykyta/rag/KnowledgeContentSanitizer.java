package com.mykyta.rag;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** Removes secret-like configuration values before content is embedded or stored in Qdrant. */
@Component
public class KnowledgeContentSanitizer {
    private static final Set<String> SENSITIVE_PARTS = Set.of(
            "password", "passwd", "secret", "token", "authorization", "apikey", "accesskey",
            "privatekey", "credential", "cookie");

    public String configuration(String content) {
        return content.lines().map(this::sanitizeLine).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private String sanitizeLine(String line) {
        int separator = separator(line);
        if (separator < 0) return line;
        String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (SENSITIVE_PARTS.stream().noneMatch(key::contains)) return line;
        return line.substring(0, separator + 1) + " [REDACTED]";
    }

    private int separator(String line) {
        int colon = line.indexOf(':');
        int equals = line.indexOf('=');
        if (colon < 0) return equals;
        if (equals < 0) return colon;
        return Math.min(colon, equals);
    }
}

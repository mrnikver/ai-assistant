package com.mykyta.observability;

import com.mykyta.config.TraceProperties;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Central bounded recursive sanitizer shared by LLM logging and tracing. */
@Component
public class LlmObservabilitySanitizer {
    private static final int MAX_DEPTH = 4;
    private static final int MAX_ITEMS = 20;
    private static final Set<String> SENSITIVE_PARTS = Set.of(
            "password", "passwd", "secret", "token", "authorization", "apikey", "accesskey",
            "privatekey", "credential", "cookie");
    private final int maxChars;

    public LlmObservabilitySanitizer(TraceProperties properties) {
        this.maxChars = properties.llmPreviewMaxChars();
    }

    public String preview(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("[\\r\\n]+", " ")
                .replaceAll("(?i)(password|passwd|secret|token|authorization|api[_-]?key|access[_-]?key|private[_-]?key|credential|cookie)(\\s*[=:]\\s*)[^,;\\s}]+", "$1$2[REDACTED]")
                .replaceAll("(?i)(\"(?:password|passwd|secret|token|authorization|api[_-]?key|access[_-]?key|private[_-]?key|credential|cookie)\"\\s*:\\s*)\"[^\"]*\"", "$1\"[REDACTED]\"")
                .trim();
        return normalized.length() <= maxChars ? normalized
                : normalized.substring(0, maxChars) + "... [truncated]";
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> map(Map<String, ?> values) {
        return (Map<String, Object>) sanitize(values, 0);
    }

    private Object sanitize(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (depth >= MAX_DEPTH) return "[max depth]";
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ == MAX_ITEMS) { result.put("...", "[truncated items]"); break; }
                String key = String.valueOf(entry.getKey());
                result.put(key, sensitive(key) ? "[REDACTED]" : sanitize(entry.getValue(), depth + 1));
            }
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) {
                if (result.size() == MAX_ITEMS) { result.add("[truncated items]"); break; }
                result.add(sanitize(item, depth + 1));
            }
            return result;
        }
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            for (int index = 0; index < Math.min(Array.getLength(value), MAX_ITEMS); index++)
                result.add(sanitize(Array.get(value, index), depth + 1));
            if (Array.getLength(value) > MAX_ITEMS) result.add("[truncated items]");
            return result;
        }
        return preview(String.valueOf(value));
    }

    private boolean sensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_PARTS.stream().anyMatch(normalized::contains);
    }
}

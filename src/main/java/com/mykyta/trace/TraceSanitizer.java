package com.mykyta.trace;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Applies conservative size and secret-name rules before model-derived data enters trace metadata. */
public final class TraceSanitizer {
    private static final int MAX_TEXT_LENGTH = 200;

    private TraceSanitizer() { }

    /**
     * Sanitizes tool arguments while preserving enough information for diagnosis.
     * @param arguments model-generated tool arguments
     * @return bounded values with secret-like keys redacted
     */
    public static Map<String, Object> arguments(Map<String, Object> arguments) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        arguments.forEach((key, value) -> sanitized.put(key, sensitive(key) ? "[redacted]" : value(value)));
        return sanitized;
    }

    /**
     * Produces a bounded single-line value suitable for trace metadata.
     * @param value potentially unbounded text
     * @return single-line bounded trace text
     */
    public static String text(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= MAX_TEXT_LENGTH ? normalized : normalized.substring(0, MAX_TEXT_LENGTH) + "…";
    }

    private static Object value(Object value) {
        if (value instanceof Number || value instanceof Boolean || value == null) return value;
        return text(String.valueOf(value));
    }

    private static boolean sensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("secret") || normalized.contains("token")
                || normalized.contains("credential") || normalized.contains("authorization");
    }
}

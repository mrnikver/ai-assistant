package com.mykyta.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** A searchable piece of a project source file and its navigation metadata. */
public record KnowledgeChunk(
        String text,
        String project,
        String sourcePath,
        KnowledgeSourceType sourceType,
        String language,
        String context,
        int startLine,
        int endLine,
        int chunkIndex
) {
    public Map<String, Object> payload(String indexRunId, String indexScope) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text);
        payload.put("source_type", sourceType.name());
        payload.put("index_scope", indexScope);
        payload.put("project", project);
        payload.put("source_path", sourcePath);
        payload.put("language", language);
        payload.put("context", context);
        payload.put("start_line", startLine);
        payload.put("end_line", endLine);
        payload.put("chunk_index", chunkIndex);
        payload.put("index_run_id", indexRunId);
        return payload;
    }
}

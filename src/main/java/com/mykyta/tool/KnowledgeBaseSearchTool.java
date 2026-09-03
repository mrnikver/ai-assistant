package com.mykyta.tool;

import com.mykyta.model.QdrantSearchResult;
import com.mykyta.model.KnowledgeSourceType;
import com.mykyta.rag.KnowledgeRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.mykyta.trace.AgentTracer;
import com.mykyta.trace.TraceSanitizer;
import com.mykyta.trace.TraceScope;
import com.mykyta.trace.TraceSpanType;

import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;

/**
 * Exposes the existing RAG retrieval capability as the {@code search_knowledge_base} agent tool.
 *
 * <p>This tool performs retrieval only. Embedding generation and Qdrant search
 * remain delegated to {@link KnowledgeRetriever} and its existing collaborators.
 * It does not generate a final user-facing answer; it returns retrieved chunks
 * as an observation so the LLM can reason over project-specific context and
 * decide whether another action or a final answer is appropriate.</p>
 */
@Component
@Slf4j
public class KnowledgeBaseSearchTool implements Tool {

    /** Function name exposed to Ollama and accepted by the registry. */
    public static final String NAME = "search_knowledge_base";

    /** Number of chunks returned when the model omits {@code topK}. */
    public static final int DEFAULT_TOP_K = 3;

    /** Largest retrieval request accepted to bound context growth. */
    public static final int MAX_TOP_K = 10;

    private static final Map<String, Object> DEFINITION = Map.of(
            "type", "function",
            "function", Map.of(
                    "name", NAME,
                    "description", "Search authoritative evidence about this application, including project architecture, "
                            + "source code, implementation details, configuration, design decisions, RAG, agents, tools, "
                            + "memory, tracing, runbooks, and documentation. Use it before answering project-specific "
                            + "implementation questions; it is not only a generic documentation search. Do not call it "
                            + "for general knowledge when retrieval is unnecessary.",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "query", Map.of(
                                            "type", "string",
                                            "description", "Focused semantic query for the internal knowledge base"
                                    ),
                                    "topK", Map.of(
                                            "type", "integer",
                                            "description", "Number of relevant chunks to return (default 3, maximum 10)",
                                            "minimum", 1,
                                            "maximum", MAX_TOP_K
                                    ),
                                    "sourceTypes", Map.of(
                                            "type", "array",
                                            "description", "Optional evidence categories to search. Use RUNBOOK for procedures; SOURCE_CODE and DOCUMENTATION for implementation questions. MOCK_RUNTIME is unavailable.",
                                            "items", Map.of("type", "string", "enum", searchableSourceTypeNames())
                                    )
                            ),
                            "required", List.of("query")
                    )
            )
    );

    private final KnowledgeRetriever knowledgeRetriever;
    private final AgentTracer agentTracer;

    /**
     * Creates the agent-facing adapter around the existing retrieval service.
     *
     * @param knowledgeRetriever embedding and vector-search capability
     * @param agentTracer collector used to nest retrieval work below the tool call
     */
    public KnowledgeBaseSearchTool(KnowledgeRetriever knowledgeRetriever, AgentTracer agentTracer) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.agentTracer = agentTracer;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> definition() {
        return DEFINITION;
    }

    /**
     * Retrieves relevant chunks and formats them as an observation for the agent.
     *
     * @param arguments required {@code query} and optional {@code topK}
     * @return retrieved chunks with relevance scores, or a clear no-results observation
     * @throws InvalidToolArgumentsException if query or topK is invalid
     * @throws ToolExecutionException if embedding generation or Qdrant search fails
     */
    @Override
    public String execute(Map<String, Object> arguments) {
        String query = requireQuery(arguments.get("query"));
        int topK = resolveTopK(arguments.get("topK"));
        Set<KnowledgeSourceType> sourceTypes = resolveSourceTypes(arguments.get("sourceTypes"));
        log.info("Knowledge-base search requested: query={}, topK={}, sourceTypes={}",
                TraceSanitizer.text(query), topK, sourceTypes);

        try (TraceScope span = agentTracer.startSpan(TraceSpanType.KNOWLEDGE_SEARCH, "Knowledge-base search")) {
            span.metadata("query", TraceSanitizer.text(query));
            span.metadata("requestedTopK", arguments.getOrDefault("topK", "default"));
            span.metadata("effectiveTopK", topK);
            span.metadata("sourceTypes", sourceTypes.stream().map(Enum::name).toList());
            try {
                List<QdrantSearchResult> results = knowledgeRetriever.retrieve(query, topK, sourceTypes);
                span.metadata("resultCount", results.size());
                span.metadata("resultSourceTypes", results.stream()
                        .map(result -> result.metadata().get("source_type")).filter(value -> value != null)
                        .map(String::valueOf).distinct().sorted().toList());
                span.metadata("resultSources", results.stream()
                        .map(result -> result.metadata().get("source_path")).filter(value -> value != null)
                        .map(String::valueOf).distinct().limit(MAX_TOP_K).toList());
                if (results.isEmpty()) {
                    return "No relevant knowledge-base chunks were found.";
                }

                StringBuilder observation = new StringBuilder("Retrieved ").append(results.size())
                        .append(" knowledge-base chunks:\n");
                for (int index = 0; index < results.size(); index++) {
                    QdrantSearchResult result = results.get(index);
                    observation.append("\n[")
                            .append(index + 1)
                            .append("] score=")
                            .append(result.score())
                            .append(formatSource(result.metadata()))
                            .append('\n')
                            .append(result.text())
                            .append('\n');
                }
                return observation.toString().trim();
            } catch (RuntimeException exception) {
                span.fail(exception);
                throw new ToolExecutionException("Knowledge-base search failed", exception);
            }
        }
    }

    private String formatSource(Map<String, Object> metadata) {
        Object path = metadata.get("source_path");
        if (path == null) {
            return "";
        }
        StringBuilder source = new StringBuilder(" sourceType=")
                .append(metadata.getOrDefault("source_type", "UNKNOWN"))
                .append(" project=");
        Object project = metadata.get("project");
        source.append(project == null ? "unknown" : project).append(" path=").append(path);
        Object startLine = metadata.get("start_line");
        Object endLine = metadata.get("end_line");
        if (startLine != null) {
            source.append(':').append(startLine);
            if (endLine != null && !endLine.equals(startLine)) {
                source.append('-').append(endLine);
            }
        }
        Object context = metadata.get("context");
        if (context != null && !context.toString().isBlank()) {
            source.append(" headingOrSymbol=").append(context);
        }
        return source.toString();
    }

    private Set<KnowledgeSourceType> resolveSourceTypes(Object value) {
        EnumSet<KnowledgeSourceType> defaults = EnumSet.allOf(KnowledgeSourceType.class);
        defaults.remove(KnowledgeSourceType.MOCK_RUNTIME);
        if (value == null) return defaults;
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            throw new InvalidToolArgumentsException("Argument 'sourceTypes' must be a non-empty array");
        }
        EnumSet<KnowledgeSourceType> resolved = EnumSet.noneOf(KnowledgeSourceType.class);
        for (Object item : values) {
            if (!(item instanceof String name)) {
                throw new InvalidToolArgumentsException("Every sourceTypes value must be a string");
            }
            try {
                KnowledgeSourceType type = KnowledgeSourceType.valueOf(name);
                if (!type.knowledgeSearchable()) {
                    throw new InvalidToolArgumentsException("MOCK_RUNTIME is not available to the Knowledge Agent");
                }
                resolved.add(type);
            } catch (IllegalArgumentException exception) {
                throw new InvalidToolArgumentsException("Unknown source type: " + name);
            }
        }
        return resolved;
    }

    private static List<String> searchableSourceTypeNames() {
        return EnumSet.allOf(KnowledgeSourceType.class).stream().filter(KnowledgeSourceType::knowledgeSearchable)
                .map(Enum::name).toList();
    }

    private String requireQuery(Object value) {
        if (!(value instanceof String query) || query.isBlank()) {
            throw new InvalidToolArgumentsException("Argument 'query' must be a non-blank string");
        }
        return query.trim();
    }

    private int resolveTopK(Object value) {
        if (value == null) {
            return DEFAULT_TOP_K;
        }
        if (!(value instanceof Number number)) {
            throw new InvalidToolArgumentsException("Argument 'topK' must be an integer between 1 and " + MAX_TOP_K);
        }

        double numericValue = number.doubleValue();
        int topK = number.intValue();
        if (numericValue != topK || topK < 1 || topK > MAX_TOP_K) {
            throw new InvalidToolArgumentsException("Argument 'topK' must be an integer between 1 and " + MAX_TOP_K);
        }
        return topK;
    }
}

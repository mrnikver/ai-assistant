package com.mykyta.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.client.LLMClient;
import com.mykyta.config.AgentProperties;
import com.mykyta.exception.AgentIterationLimitException;
import com.mykyta.model.AgentResult;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.QdrantSearchResult;
import com.mykyta.model.ToolCall;
import com.mykyta.model.ToolFunction;
import com.mykyta.rag.KnowledgeRetriever;
import com.mykyta.tool.KnowledgeBaseSearchTool;
import com.mykyta.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentServiceTest {

    @Test
    void returnsAnswerWithoutSearchingKnowledgeBase() throws Exception {
        StubLlmClient llmClient = new StubLlmClient(finalMessage());
        StubKnowledgeRetriever retriever = new StubKnowledgeRetriever();

        AgentResult result = agent(llmClient, retriever, 5).run(context());

        assertEquals("Answer", result.response().answer());
        assertEquals(1, result.iterations());
        assertEquals(0, result.toolExecutions());
        assertEquals(0, retriever.queries.size());
        assertEquals(1, llmClient.invocations);
    }

    @Test
    void executesKnowledgeSearchAndReturnsChunksToLlm() throws Exception {
        StubLlmClient llmClient = new StubLlmClient(
                toolCall("search_knowledge_base", Map.of("query", "database migration", "topK", 2)),
                finalMessage()
        );
        StubKnowledgeRetriever retriever = new StubKnowledgeRetriever(
                new QdrantSearchResult("Run migrations before starting the new deployment.", 0.93),
                new QdrantSearchResult("Rollback if the schema validation fails.", 0.84)
        );
        List<LLMMessage> context = context();

        AgentResult result = agent(llmClient, retriever, 5).run(context);

        assertEquals(2, result.iterations());
        assertEquals(1, result.toolExecutions());
        assertEquals(List.of(new SearchQuery("database migration", 2)), retriever.queries);
        assertTrue(context.stream().anyMatch(message -> "tool".equals(message.role())
                && message.content().contains("Run migrations before starting")));
    }

    @Test
    void invokesLlmAgainWithToolObservation() throws Exception {
        StubLlmClient llmClient = new StubLlmClient(
                toolCall("search_knowledge_base", Map.of("query", "deploy")),
                finalMessage()
        );
        StubKnowledgeRetriever retriever = new StubKnowledgeRetriever(
                new QdrantSearchResult("Deployment observation", 0.9)
        );
        llmClient.beforeSecondInvocation = messages -> assertTrue(messages.stream()
                .anyMatch(message -> "tool".equals(message.role())
                        && message.content().contains("Deployment observation")));

        agent(llmClient, retriever, 5).run(context());

        assertEquals(2, llmClient.invocations);
    }

    @Test
    void supportsMultipleSequentialToolIterations() throws Exception {
        StubLlmClient llmClient = new StubLlmClient(
                toolCall("search_knowledge_base", Map.of("query", "deployment")),
                toolCall("search_knowledge_base", Map.of("query", "rollback", "topK", 1)),
                finalMessage()
        );
        StubKnowledgeRetriever retriever = new StubKnowledgeRetriever(
                new QdrantSearchResult("Relevant chunk", 0.8)
        );

        AgentResult result = agent(llmClient, retriever, 5).run(context());

        assertEquals(3, result.iterations());
        assertEquals(2, result.toolExecutions());
        assertEquals(List.of(
                new SearchQuery("deployment", KnowledgeBaseSearchTool.DEFAULT_TOP_K),
                new SearchQuery("rollback", 1)
        ), retriever.queries);
    }

    @Test
    void returnsUnknownToolAsControlledObservation() throws Exception {
        StubLlmClient llmClient = new StubLlmClient(
                toolCall("delete_database", Map.of()),
                finalMessage()
        );
        StubKnowledgeRetriever retriever = new StubKnowledgeRetriever();
        List<LLMMessage> context = context();

        AgentResult result = agent(llmClient, retriever, 5).run(context);

        assertEquals(1, result.toolExecutions());
        assertTrue(context.stream().anyMatch(message -> "tool".equals(message.role())
                && message.content().contains("Unknown tool: delete_database")));
        assertEquals(0, retriever.queries.size());
    }

    @Test
    void returnsInvalidArgumentsAsControlledObservation() throws Exception {
        StubLlmClient llmClient = new StubLlmClient(
                toolCall("search_knowledge_base", Map.of("query", " ", "topK", 50)),
                finalMessage()
        );
        StubKnowledgeRetriever retriever = new StubKnowledgeRetriever();
        List<LLMMessage> context = context();

        agent(llmClient, retriever, 5).run(context);

        assertTrue(context.stream().anyMatch(message -> "tool".equals(message.role())
                && message.content().contains("must be a non-blank string")));
        assertEquals(0, retriever.queries.size());
    }

    @Test
    void stopsAtMaximumIterationLimit() {
        LLMMessage repeatedCall = toolCall("search_knowledge_base", Map.of("query", "loop"));
        StubLlmClient llmClient = new StubLlmClient(repeatedCall, repeatedCall);
        StubKnowledgeRetriever retriever = new StubKnowledgeRetriever(new QdrantSearchResult("Chunk", 0.7));

        assertThrows(AgentIterationLimitException.class, () -> agent(llmClient, retriever, 2).run(context()));
        assertEquals(2, llmClient.invocations);
    }

    private AgentService agent(LLMClient llmClient, KnowledgeRetriever retriever, int maxIterations) {
        KnowledgeBaseSearchTool searchTool = new KnowledgeBaseSearchTool(retriever);
        return new AgentService(llmClient, new ToolRegistry(List.of(searchTool)), new AgentProperties(maxIterations));
    }

    private List<LLMMessage> context() {
        return new ArrayList<>(List.of(new LLMMessage("user", "Question")));
    }

    private static LLMMessage toolCall(String name, Map<String, Object> arguments) {
        return new LLMMessage("assistant", null,
                List.of(new ToolCall("call-1", new ToolFunction(0, name, arguments))), null);
    }

    private static LLMMessage finalMessage() {
        return new LLMMessage("assistant", "{\"answer\":\"Answer\",\"confidence\":\"HIGH\"}");
    }

    private static final class StubLlmClient extends LLMClient {

        private final Deque<LLMMessage> responses;
        private int invocations;
        private Consumer<List<LLMMessage>> beforeSecondInvocation = messages -> { };

        private StubLlmClient(LLMMessage... responses) {
            super("http://localhost:11434", "test-model", new ObjectMapper(), Map.of());
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public LLMMessage chatWithTools(List<LLMMessage> messages, List<Map<String, Object>> tools) {
            invocations++;
            if (invocations == 2) {
                beforeSecondInvocation.accept(messages);
            }
            return responses.removeFirst();
        }
    }

    private static final class StubKnowledgeRetriever implements KnowledgeRetriever {

        private final List<QdrantSearchResult> results;
        private final List<SearchQuery> queries = new ArrayList<>();

        private StubKnowledgeRetriever(QdrantSearchResult... results) {
            this.results = List.of(results);
        }

        @Override
        public List<QdrantSearchResult> retrieve(String query, int limit) {
            queries.add(new SearchQuery(query, limit));
            return results.stream().limit(limit).toList();
        }
    }

    private record SearchQuery(String query, int limit) {
    }
}

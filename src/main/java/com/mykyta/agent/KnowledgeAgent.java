package com.mykyta.agent;

import com.mykyta.config.AgentProperties;
import com.mykyta.model.AgentResult;
import com.mykyta.model.LLMMessage;
import com.mykyta.tool.KnowledgeBaseSearchTool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/** Independent documentation specialist with access only to RAG retrieval. */
@Service
public class KnowledgeAgent {
    private static final String PROMPT = """
            You are the Knowledge Agent, a documentation and project-knowledge specialist.
            Investigate internal documentation, source knowledge, and runbooks with search_knowledge_base.
            Do not claim to know current runtime or deployment state; you have no runtime capabilities.
            Return JSON with exactly "answer" and "confidence" (LOW, MEDIUM, or HIGH).
            """;
    private final AgentRuntime runtime;
    private final AgentDefinition definition;

    public KnowledgeAgent(AgentRuntime runtime, KnowledgeBaseSearchTool knowledgeTool, AgentProperties properties) {
        this.runtime = runtime;
        this.definition = new AgentDefinition("Knowledge Agent", AgentType.KNOWLEDGE, PROMPT,
                List.of(knowledgeTool), properties.knowledgeMaxIterations(), "Supervisor Agent");
    }

    public AgentResult investigate(String task) throws IOException, InterruptedException {
        return runtime.run(definition, List.of(new LLMMessage("user", task)), 0, 0, task);
    }
}

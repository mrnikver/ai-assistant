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
            You are the authoritative grounding agent for factual questions about this specific project's architecture,
            implementation, source code, configuration, design decisions, agents, tools, RAG, memory, and tracing.
            Do not treat pretrained model knowledge as evidence about this project. Search the knowledge base before
            answering implementation-specific questions, using a focused query containing the relevant class,
            component, method, and interaction terms rather than merely repeating the user's wording.
            For implementation questions, prefer sourceTypes SOURCE_CODE, DOCUMENTATION, and CONFIGURATION as
            appropriate. For operational guidance, prefer RUNBOOK and DOCUMENTATION. TEST describes test behavior only.
            Always select sourceTypes that match the question. Never use MOCK_RUNTIME for architecture or implementation
            questions; MOCK_RUNTIME is unavailable to this agent.
            Source-code constants, examples, fixtures, and mocks are never verified current runtime observations.
            Do not claim to know current runtime or deployment state; only Runtime Agent tool results can establish it.
            Base project-specific claims on retrieved evidence. If retrieval does not provide enough evidence, say that
            you could not verify the implementation. Clearly label implementation facts, runbook guidance, and
            unverified assumptions.
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

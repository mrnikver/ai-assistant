package com.mykyta.agent;

import com.mykyta.config.AgentProperties;
import com.mykyta.model.AgentResult;
import com.mykyta.model.LLMMessage;
import com.mykyta.tool.InvalidToolArgumentsException;
import com.mykyta.tool.Tool;
import com.mykyta.tool.ToolExecutionException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Owns user-facing reasoning and delegates investigation to isolated specialist agents. */
@Service
public class SupervisorAgent {
    /** Policy boundary that keeps claims about this application grounded in indexed project evidence. */
    private static final String PROJECT_KNOWLEDGE_REQUIRED = """
            PROJECT_KNOWLEDGE_REQUIRED:
            You do not have authoritative knowledge about this application's implementation from pretrained knowledge.
            Before making factual claims about this specific application's architecture, agents, Supervisor or Runtime
            behavior, tool orchestration, ToolRegistry, RAG, memory, tracing, LLM calls, configuration, backend,
            frontend, or source code, delegate a focused investigation to ask_knowledge_agent.
            You may answer generic conceptual questions directly. For example, "What is RAG?" is generic, while
            "How is RAG implemented in this project?" requires project knowledge. Likewise, "What is a supervisor
            agent?" is generic, while "Does our SupervisorAgent call the LLM?" requires project knowledge.
            If a Knowledge Agent observation already present in the current context sufficiently answers the question,
            do not delegate again. Treat retrieved project evidence as authoritative when it conflicts with a prior
            model assumption. Never substitute typical AI-agent behavior for this project's actual behavior.
            If the Knowledge Agent cannot find enough evidence, say that the implementation could not be verified;
            do not invent architecture or implementation details.
            """;

    private static final String PROMPT = """
            You are the Supervisor Agent for a deployment investigation assistant.
            You own the final user answer but cannot execute domain-specific tools directly.
            """ + PROJECT_KNOWLEDGE_REQUIRED + """
            Delegate documentation and runbook questions to ask_knowledge_agent.
            Delegate current service health, deployment state, and operational questions to ask_runtime_agent.
            Delegate to both when the request needs runtime evidence and documented guidance, then synthesize their observations.
            In combined answers clearly separate verified runtime evidence, documentation/runbook guidance,
            likely interpretation, and anything not verified. Never present documentation as current runtime fact.
            Do not invent specialist results. Keep the final answer concise and technical.
            Return JSON with exactly "answer" and "confidence" (LOW, MEDIUM, or HIGH).
            """;

    private final AgentRuntime runtime;
    private final AgentDefinition definition;

    public SupervisorAgent(AgentRuntime runtime, KnowledgeAgent knowledgeAgent,
                           RuntimeAgent runtimeAgent, AgentProperties properties) {
        this.runtime = runtime;
        this.definition = new AgentDefinition("Supervisor Agent", AgentType.SUPERVISOR, PROMPT,
                List.of(
                        new DelegationTool("ask_knowledge_agent",
                                "Delegate questions requiring authoritative knowledge about this application, including "
                                        + "its architecture, implementation, source code, configuration, design decisions, "
                                        + "RAG, agents, tools, memory, tracing, runbooks, and documentation. Use this before "
                                        + "making project-specific factual claims.",
                                knowledgeAgent::investigate),
                        new DelegationTool("ask_runtime_agent",
                                "Ask the runtime specialist to investigate current mocked deployment state or logs.",
                                runtimeAgent::investigate)
                ), properties.supervisorMaxIterations(), null);
    }

    public AgentResult run(List<LLMMessage> context, int historyMessageCount, int memoryCount, String userRequest)
            throws IOException, InterruptedException {
        return runtime.run(definition, context, historyMessageCount, memoryCount, userRequest);
    }

    @FunctionalInterface
    private interface Investigation { AgentResult run(String question) throws IOException, InterruptedException; }

    /** Internal delegation boundary; only the Supervisor definition receives these tools. */
    private record DelegationTool(String name, String description, Investigation investigation) implements Tool {
        @Override public Map<String, Object> definition() {
            return Map.of("type", "function", "function", Map.of(
                    "name", name, "description", description,
                    "parameters", Map.of("type", "object", "properties", Map.of(
                            "question", Map.of("type", "string", "description", "Focused investigation for the specialist")
                    ), "required", List.of("question"))));
        }

        @Override public String execute(Map<String, Object> arguments) {
            Object value = arguments.get("question");
            if (!(value instanceof String question) || question.isBlank()) {
                throw new InvalidToolArgumentsException("Argument 'question' must be a non-blank string");
            }
            try {
                AgentResult result = investigation.run(question.trim());
                return "Specialist finding (confidence=" + result.response().confidence() + "): "
                        + result.response().answer();
            } catch (IOException exception) {
                throw new ToolExecutionException("Specialist investigation failed", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ToolExecutionException("Specialist investigation was interrupted", exception);
            }
        }
    }
}

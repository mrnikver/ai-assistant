package com.mykyta.service;

import com.mykyta.config.AssistantProperties;
import com.mykyta.entity.Memory;
import com.mykyta.model.AgentResult;
import com.mykyta.model.AssistantExecution;
import com.mykyta.model.LLMMessage;
import com.mykyta.response.AssistantResponse;
import com.mykyta.response.MemoryExtractionResponse;
import com.mykyta.response.TraceSummaryResponse;
import com.mykyta.trace.AgentTrace;
import com.mykyta.trace.AgentTracer;
import com.mykyta.trace.TraceScope;
import com.mykyta.trace.TraceSpanType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Prepares application context around the agent and persists completed conversations.
 *
 * <p>This service owns memory extraction, persistent-memory context, and chat
 * history. It intentionally does not perform RAG retrieval: project knowledge is
 * now available only through an agent tool, allowing the LLM to decide when the
 * additional external lookup is useful.</p>
 */
@Service
@Slf4j
public class AssistantService {

    private static final String SYSTEM_PROMPT = """
            You are a deployment investigation assistant.
            Help software engineers investigate deployment failures.
            Decide whether registered tools are needed before answering.
            Use knowledge-base search for internal documentation, runbooks,
            troubleshooting procedures, or other project-specific facts.
            Keep answers concise and technical.
            When answering, return JSON with exactly two fields:
            "answer" (a string) and "confidence" (LOW, MEDIUM, or HIGH).
            """;


    private final ConversationService conversationService;
    private final AgentService agentService;
    private final AssistantProperties assistantProperties;
    private final MemoryService memoryService;
    private final MemoryExtractorService memoryExtractorService;
    private final AgentTracer agentTracer;
    private final TraceService traceService;


    /**
     * Creates the request-level coordinator and its application context collaborators.
     *
     * @param conversationService recent conversation storage
     * @param agentService bounded tool-calling agent
     * @param assistantProperties conversation-context limits
     * @param memoryService persistent structured memory
     * @param memoryExtractorService model-based durable-memory extractor
     * @param agentTracer root execution-trace lifecycle manager
     * @param traceService completed trace retention and summary service
     */
    public AssistantService(
            ConversationService conversationService,
            AgentService agentService,
            AssistantProperties assistantProperties,
            MemoryService memoryService,
            MemoryExtractorService memoryExtractorService,
            AgentTracer agentTracer,
            TraceService traceService
    ) {
        this.conversationService = conversationService;
        this.agentService = agentService;
        this.assistantProperties = assistantProperties;
        this.memoryService = memoryService;
        this.memoryExtractorService = memoryExtractorService;
        this.agentTracer = agentTracer;
        this.traceService = traceService;
    }

    /**
     * Handles one user turn and returns the answer produced by the agent loop.
     *
     * @param conversationId stable identifier used to load and store recent messages
     * @param userMessage current user request
     * @return final structured assistant response
     * @throws Exception if memory extraction, LLM communication, or agent execution fails
     */
    public AssistantExecution chat(
            String conversationId,
            String userMessage
    ) throws Exception {

        AgentTracer.TraceSession traceSession = agentTracer.beginTrace();
        AssistantResponse answer;
        try {
            answer = executeAssistantFlow(conversationId, userMessage);
        } catch (Exception exception) {
            traceSession.fail(exception);
            traceSession.close();
            traceService.save(traceSession.completedTrace());
            throw exception;
        }

        traceSession.close();
        AgentTrace trace = traceSession.completedTrace();
        TraceSummaryResponse summary = traceService.save(trace);
        return new AssistantExecution(answer, summary);
    }

    private AssistantResponse executeAssistantFlow(String conversationId, String userMessage) throws Exception {

        List<LLMMessage> context = new ArrayList<>();
        log.info("Assistant flow started");

        log.debug("Starting persistent memory extraction");
        MemoryExtractionResponse memoryCandidate =
                memoryExtractorService.extract(userMessage);

        if (memoryCandidate.shouldStore()) {
            log.info("Durable memory detected: key={}", memoryCandidate.key());
            memoryService.save(
                    memoryCandidate.key(),
                    memoryCandidate.value()
            );
        } else {
            log.debug("No durable memory detected");
        }

        context.add(
                new LLMMessage(
                        "system",
                        SYSTEM_PROMPT
                )
        );

        List<Memory> memories = memoryService.getAll();
        log.debug("Adding persistent memory to context: count={}", memories.size());

        if (!memories.isEmpty()) {

            String memoryContext = memories.stream()
                    .map(memory -> memory.getKey() + ": " + memory.getValue())
                    .collect(Collectors.joining("\n"));

            context.add(
                    new LLMMessage(
                            "system",
                            """
                            Persistent application memory:
        
                            %s
        
                            Use this information when it is relevant to the user's request.
                            """.formatted(memoryContext)
                    )
            );
        }

        context.addAll(
                conversationService.getRecentMessages(
                        conversationId,
                        assistantProperties.historyLimit()
                )
        );
        log.debug("Recent conversation history added: contextMessageCount={}", context.size());

        LLMMessage currentUserMessage = new LLMMessage("user", userMessage);

        context.add(currentUserMessage);

        AgentResult agentResult = agentService.run(context);
        AssistantResponse answer = agentResult.response();

        log.debug(
                "Agent stage completed: iterations={}, toolExecutions={}, contextMessageCount={}",
                agentResult.iterations(),
                agentResult.toolExecutions(),
                context.size()
        );

        conversationService.add(conversationId, currentUserMessage);

        conversationService.add(conversationId, new LLMMessage("assistant", answer.answer()));

        try (TraceScope finalResponse = agentTracer.startSpan(TraceSpanType.FINAL_RESPONSE, "Final response")) {
            finalResponse.metadata("confidence", answer.confidence().name());
            finalResponse.metadata("answerLength", answer.answer().length());
            log.info("Assistant flow completed: confidence={}", answer.confidence());
        }

        return answer;
    }

}

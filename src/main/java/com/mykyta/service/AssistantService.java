package com.mykyta.service;

import com.mykyta.config.AssistantProperties;
import com.mykyta.entity.Memory;
import com.mykyta.agent.SupervisorAgent;
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
import com.mykyta.tool.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Prepares application context around the agent and persists completed conversations.
 *
 * <p>This service owns memory extraction, persistent-memory context, and chat
 * history. Domain investigation is delegated through the Supervisor, so this
 * coordinator never performs RAG or runtime lookup directly.</p>
 */
@Service
@Slf4j
public class AssistantService {

    private final ConversationService conversationService;
    private final SupervisorAgent supervisorAgent;
    private final AssistantProperties assistantProperties;
    private final MemoryService memoryService;
    private final MemoryExtractorService memoryExtractorService;
    private final AgentTracer agentTracer;
    private final TraceService traceService;
    private final RestartConfirmationPolicy restartConfirmationPolicy;


    /**
     * Creates the request-level coordinator and its application context collaborators.
     *
     * @param conversationService recent conversation storage
     * @param supervisorAgent top-level agent that delegates domain investigation
     * @param assistantProperties conversation-context limits
     * @param memoryService persistent structured memory
     * @param memoryExtractorService model-based durable-memory extractor
     * @param agentTracer root execution-trace lifecycle manager
     * @param traceService completed trace retention and summary service
     */
    public AssistantService(
            ConversationService conversationService,
            SupervisorAgent supervisorAgent,
            AssistantProperties assistantProperties,
            MemoryService memoryService,
            MemoryExtractorService memoryExtractorService,
            AgentTracer agentTracer,
            TraceService traceService,
            RestartConfirmationPolicy restartConfirmationPolicy
    ) {
        this.conversationService = conversationService;
        this.supervisorAgent = supervisorAgent;
        this.assistantProperties = assistantProperties;
        this.memoryService = memoryService;
        this.memoryExtractorService = memoryExtractorService;
        this.agentTracer = agentTracer;
        this.traceService = traceService;
        this.restartConfirmationPolicy = restartConfirmationPolicy;
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
        restartConfirmationPolicy.acceptUserMessage(conversationId, userMessage);

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

        List<LLMMessage> recentMessages = conversationService.getRecentMessages(
                conversationId, assistantProperties.historyLimit());
        context.addAll(recentMessages);
        log.debug("Recent conversation history added: contextMessageCount={}", context.size());

        LLMMessage currentUserMessage = new LLMMessage("user", userMessage);

        context.add(currentUserMessage);

        AgentResult agentResult = supervisorAgent.run(context, recentMessages.size(), memories.size(), userMessage,
                new ToolExecutionContext(conversationId));
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

package com.mykyta.service;

import com.mykyta.client.LLMClient;
import com.mykyta.config.AssistantProperties;
import com.mykyta.entity.Memory;
import com.mykyta.model.LLMMessage;
import com.mykyta.rag.KnowledgeRetriever;
import com.mykyta.response.AssistantResponse;
import com.mykyta.response.MemoryExtractionResponse;
import com.mykyta.tool.ToolDispatcher;
import com.mykyta.util.JsonResourceLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AssistantService {

    private static final String SYSTEM_PROMPT = """
            You are a deployment investigation assistant.
            Help software engineers investigate deployment failures.
            Keep answers concise and technical.
            """;


    private final LLMClient llmClient;
    private final ConversationService conversationService;
    private final JsonResourceLoader jsonResourceLoader;
    private final AgentService agentService;
    private final AssistantProperties assistantProperties;
    private final KnowledgeRetriever knowledgeRetriever;
    private final MemoryService memoryService;
    private final MemoryExtractorService memoryExtractorService;


    public AssistantService(
            LLMClient llmClient,
            ConversationService conversationService,
            JsonResourceLoader jsonResourceLoader,
            AgentService agentService,
            AssistantProperties assistantProperties,
            KnowledgeRetriever knowledgeRetriever, MemoryService memoryService, MemoryExtractorService memoryExtractorService
    ) {
        this.llmClient = llmClient;
        this.conversationService = conversationService;
        this.jsonResourceLoader = jsonResourceLoader;
        this.agentService = agentService;
        this.assistantProperties = assistantProperties;
        this.knowledgeRetriever = knowledgeRetriever;
        this.memoryService = memoryService;
        this.memoryExtractorService = memoryExtractorService;
    }

    public AssistantResponse chat(
            String conversationId,
            String userMessage
    ) throws Exception {

        List<LLMMessage> context = new ArrayList<>();

        // 1. Extract persistent memory from the current user message
        MemoryExtractionResponse memoryCandidate =
                memoryExtractorService.extract(userMessage);

        System.out.println("Memory candidate: " + memoryCandidate);

        if (memoryCandidate.shouldStore()) {
            memoryService.save(
                    memoryCandidate.key(),
                    memoryCandidate.value()
            );
        }

        context.add(
                new LLMMessage(
                        "system",
                        SYSTEM_PROMPT
                )
        );

        List<Memory> memories = memoryService.getAll();

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

        String retrievedKnowledge = knowledgeRetriever.retrieve(
                        userMessage
                );

//        System.out.println(retrievedKnowledge);

        context.add(
                new LLMMessage(
                        "system",
                        """
                        Relevant deployment documentation retrieved for this request:
        
                        --- BEGIN DOCUMENTATION ---
                        %s
                        --- END DOCUMENTATION ---
        
                        Use the retrieved documentation as the primary source of truth.
                        Do not invent additional troubleshooting steps that are not supported
                        by the retrieved documentation.
                        If the documentation is insufficient, say so explicitly.
                        """.formatted(retrievedKnowledge)
                )
        );

        context.addAll(
                conversationService.getRecentMessages(
                        conversationId,
                        assistantProperties.historyLimit()
                )
        );

        LLMMessage currentUserMessage =
                new LLMMessage(
                        "user",
                        userMessage
                );

        context.add(currentUserMessage);

        Map<String, Object> deploymentStatusTool =
                jsonResourceLoader.load(
                        "tools/get-deployment-status.json"
                );

        Map<String, Object> deploymentLogsTool =
                jsonResourceLoader.load(
                        "tools/get-deployment-logs.json"
                );

        List<Map<String, Object>> tools =
                List.of(
                        deploymentStatusTool,
                        deploymentLogsTool
                );

        // Agent performs zero or more tool interactions.
        agentService.run(
                context,
                tools
        );

        // Generate final structured response.
        AssistantResponse answer = llmClient.chat(context);

        conversationService.add(
                conversationId,
                currentUserMessage
        );

        conversationService.add(
                conversationId,
                new LLMMessage(
                        "assistant",
                        answer.answer()
                )
        );

        return answer;
    }

}

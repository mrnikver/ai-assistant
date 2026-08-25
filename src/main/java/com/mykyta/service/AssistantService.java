package com.mykyta.service;

import com.mykyta.client.LLMClient;
import com.mykyta.config.AssistantProperties;
import com.mykyta.model.AssistantResponse;
import com.mykyta.model.LLMMessage;
import com.mykyta.rag.KnowledgeRetriever;
import com.mykyta.tool.ToolDispatcher;
import com.mykyta.util.JsonResourceLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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


    public AssistantService(
            LLMClient llmClient,
            ConversationService conversationService,
            JsonResourceLoader jsonResourceLoader,
            AgentService agentService,
            AssistantProperties assistantProperties,
            KnowledgeRetriever knowledgeRetriever
    ) {
        this.llmClient = llmClient;
        this.conversationService = conversationService;
        this.jsonResourceLoader = jsonResourceLoader;
        this.agentService = agentService;
        this.assistantProperties = assistantProperties;
        this.knowledgeRetriever = knowledgeRetriever;
    }

    public AssistantResponse chat(
            String conversationId,
            String userMessage
    ) throws Exception {

        List<LLMMessage> context = new ArrayList<>();

        context.add(
                new LLMMessage(
                        "system",
                        SYSTEM_PROMPT
                )
        );

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

package com.mykyta.service;

import com.mykyta.client.LLMClient;
import com.mykyta.model.AssistantResponse;
import com.mykyta.model.LLMMessage;
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

    private static final int HISTORY_LIMIT = 10;

    private final LLMClient llmClient;
    private final ConversationService conversationService;
    private final JsonResourceLoader jsonResourceLoader;
    private final AgentService agentService;


    public AssistantService(
            LLMClient llmClient,
            ConversationService conversationService,
            JsonResourceLoader jsonResourceLoader,
            ToolDispatcher toolDispatcher,
            AgentService agentService
    ) {
        this.llmClient = llmClient;
        this.conversationService = conversationService;
        this.jsonResourceLoader = jsonResourceLoader;
        this.agentService = agentService;
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

        context.addAll(
                conversationService.getRecentMessages(
                        conversationId,
                        HISTORY_LIMIT
                )
        );

        LLMMessage currentUserMessage =
                new LLMMessage(
                        "user",
                        userMessage
                );

        context.add(currentUserMessage);

        Map<String, Object> deploymentTool =
                jsonResourceLoader.load("tools/get-deployment-status.json");

        List<Map<String, Object>> tools = List.of(deploymentTool);

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

package com.mykyta.service;

import com.mykyta.client.LLMClient;
import com.mykyta.model.AssistantResponse;
import com.mykyta.model.LLMMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    public AssistantService(
            LLMClient llmClient,
            ConversationService conversationService
    ) {
        this.llmClient = llmClient;
        this.conversationService = conversationService;
    }

    public AssistantResponse chat(String conversationId, String userMessage) throws Exception {

        List<LLMMessage> context = new ArrayList<>();

        // 1. System prompt
        context.add(
                new LLMMessage("system", SYSTEM_PROMPT)
        );

        // 2. Previous conversation
        context.addAll(
                conversationService.getRecentMessages(conversationId, HISTORY_LIMIT)
        );

        // 3. Current user userMessage
        LLMMessage currentUserMessage = new LLMMessage("user", userMessage);

        context.add(currentUserMessage);

        // 4. LLM call
        AssistantResponse answer = llmClient.chat(context);

        // 5. Save this interaction
        conversationService.add(
                conversationId,
                currentUserMessage
        );

        conversationService.add(
                conversationId,
                new LLMMessage("assistant", answer.answer())
        );

        return answer;
    }
}
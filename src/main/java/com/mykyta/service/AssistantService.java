package com.mykyta.service;

import com.mykyta.client.LLMClient;
import com.mykyta.model.AssistantResponse;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.ToolCall;
import com.mykyta.tool.ToolDefinitionLoader;
import com.mykyta.tool.ToolDispatcher;
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
    private final ToolDefinitionLoader toolDefinitionLoader;
    private final ToolDispatcher toolDispatcher;


    public AssistantService(
            LLMClient llmClient,
            ConversationService conversationService,
            ToolDefinitionLoader toolDefinitionLoader, ToolDispatcher toolDispatcher
    ) {
        this.llmClient = llmClient;
        this.conversationService = conversationService;
        this.toolDefinitionLoader = toolDefinitionLoader;
        this.toolDispatcher = toolDispatcher;
    }

    public AssistantResponse chat(
            String conversationId,
            String userMessage
    ) throws Exception {

        List<LLMMessage> context = new ArrayList<>();

        // 1. System prompt
        context.add(
                new LLMMessage("system", SYSTEM_PROMPT)
        );

        // 2. Previous conversation
        context.addAll(
                conversationService.getRecentMessages(
                        conversationId,
                        HISTORY_LIMIT
                )
        );

        // 3. Current user message
        LLMMessage currentUserMessage =
                new LLMMessage("user", userMessage);

        context.add(currentUserMessage);

        // 4. Load available tools
        Map<String, Object> deploymentTool =
                toolDefinitionLoader.load(
                        "get-deployment-status.json"
                );

        List<Map<String, Object>> tools =
                List.of(deploymentTool);

        // 5. Ask LLM whether it needs a tool
        LLMMessage llmResponse = llmClient.chatWithTools(
                context,
                tools
        );

        List<ToolCall> toolCalls = llmResponse.toolCalls();

        // 6. Execute tool if requested
        if (toolCalls != null && !toolCalls.isEmpty()) {

            ToolCall toolCall = toolCalls.get(0);

            String toolName =
                    toolCall.function().name();
            System.out.println(toolName + " call");
            Map<String, Object> arguments =
                    toolCall.function().arguments();

            String toolResult = toolDispatcher.execute(
                            toolName,
                            arguments
                    );

            // Important:
            // add assistant tool request to context
            context.add(llmResponse);

            // add tool result to context
            context.add(
                    new LLMMessage(
                            "tool",
                            toolResult,
                            null,
                            toolName
                    )
            );
        }

        // 7. Generate final structured answer
        AssistantResponse answer =
                llmClient.chat(context);

        // 8. Store conversation
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

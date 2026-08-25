package com.mykyta.service;

import com.mykyta.client.LLMClient;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.ToolCall;
import com.mykyta.tool.ToolDispatcher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AgentService {

    private static final int MAX_AGENT_ITERATIONS = 5;

    private final LLMClient llmClient;
    private final ToolDispatcher toolDispatcher;

    public AgentService(
            LLMClient llmClient,
            ToolDispatcher toolDispatcher
    ) {
        this.llmClient = llmClient;
        this.toolDispatcher = toolDispatcher;
    }

    public void run(
            List<LLMMessage> context,
            List<Map<String, Object>> tools
    ) throws Exception {

        for (int iteration = 0;
             iteration < MAX_AGENT_ITERATIONS;
             iteration++) {

            LLMMessage llmResponse =
                    llmClient.chatWithTools(
                            context,
                            tools
                    );

            List<ToolCall> toolCalls =
                    llmResponse.toolCalls();

            // No more actions required.
            if (toolCalls == null || toolCalls.isEmpty()) {
                return;
            }

            // Preserve assistant tool request in context.
            context.add(llmResponse);

            ToolCall toolCall =
                    toolCalls.get(0);

            String toolName =
                    toolCall.function().name();

            Map<String, Object> arguments =
                    toolCall.function().arguments();
            System.out.println(toolName + " call; args: "  + arguments.toString());
            String toolResult =
                    toolDispatcher.execute(
                            toolName,
                            arguments
                    );
            System.out.println("toolResult: " + toolResult);

            // Add tool observation back to context.
            context.add(
                    new LLMMessage(
                            "tool",
                            toolResult,
                            null,
                            toolName
                    )
            );
        }
    }
}
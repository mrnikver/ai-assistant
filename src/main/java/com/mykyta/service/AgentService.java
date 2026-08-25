package com.mykyta.service;

import com.mykyta.client.LLMClient;
import com.mykyta.config.AgentProperties;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.ToolCall;
import com.mykyta.tool.ToolDispatcher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AgentService {

    private final LLMClient llmClient;
    private final ToolDispatcher toolDispatcher;
    private final AgentProperties agentProperties;

    public AgentService(
            LLMClient llmClient,
            ToolDispatcher toolDispatcher,
            AgentProperties agentProperties
    ) {
        this.llmClient = llmClient;
        this.toolDispatcher = toolDispatcher;
        this.agentProperties = agentProperties;
    }

    public void run(
            List<LLMMessage> context,
            List<Map<String, Object>> tools
    ) throws Exception {

        for (int iteration = 0; iteration < agentProperties.maxIterations(); iteration++) {
            LLMMessage llmResponse = llmClient.chatWithTools(
                            context,
                            tools
                    );

            List<ToolCall> toolCalls = llmResponse.toolCalls();

            if (toolCalls == null || toolCalls.isEmpty()) {
                return;
            }

            context.add(llmResponse);

            for (ToolCall toolCall : toolCalls) {
                String toolName = toolCall.function().name();

                Map<String, Object> arguments = toolCall.function().arguments();
                System.out.println(toolName + " call; args: " + arguments.toString());
                String toolResult = toolDispatcher.execute(
                        toolName,
                        arguments
                );
                System.out.println("toolResult: " + toolResult);

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
}

package com.mykyta.service;

import com.mykyta.client.LLMClient;
import com.mykyta.config.AgentProperties;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.ToolCall;
import com.mykyta.tool.ToolDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
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

        log.info(
                "Agent loop started: maxIterations={}, availableTools={}, contextMessageCount={}",
                agentProperties.maxIterations(),
                tools.size(),
                context.size()
        );

        for (int iteration = 0; iteration < agentProperties.maxIterations(); iteration++) {
            int iterationNumber = iteration + 1;
            long startedAt = System.nanoTime();
            log.debug("Agent iteration started: iteration={}", iterationNumber);

            LLMMessage llmResponse = llmClient.chatWithTools(
                            context,
                            tools
                    );

            List<ToolCall> toolCalls = llmResponse.toolCalls();

            if (toolCalls == null || toolCalls.isEmpty()) {
                log.info(
                        "Agent loop completed without tool call: iteration={}, durationMs={}",
                        iterationNumber,
                        elapsedMilliseconds(startedAt)
                );
                return;
            }

            log.info(
                    "Agent requested tools: iteration={}, toolCallCount={}, durationMs={}",
                    iterationNumber,
                    toolCalls.size(),
                    elapsedMilliseconds(startedAt)
            );

            context.add(llmResponse);

            for (ToolCall toolCall : toolCalls) {
                String toolName = toolCall.function().name();

                Map<String, Object> arguments = toolCall.function().arguments();
                log.info("Tool execution started: tool={}, argumentNames={}", toolName, arguments.keySet());
                long toolStartedAt = System.nanoTime();
                String toolResult = toolDispatcher.execute(
                        toolName,
                        arguments
                );
                log.info(
                        "Tool execution completed: tool={}, resultLength={}, durationMs={}",
                        toolName,
                        toolResult.length(),
                        elapsedMilliseconds(toolStartedAt)
                );

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

        log.warn("Agent loop reached maximum iterations: maxIterations={}", agentProperties.maxIterations());
    }

    private long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

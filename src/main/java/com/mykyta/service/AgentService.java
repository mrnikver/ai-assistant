package com.mykyta.service;

import com.mykyta.client.LLMClient;
import com.mykyta.config.AgentProperties;
import com.mykyta.exception.AgentIterationLimitException;
import com.mykyta.model.AgentResult;
import com.mykyta.model.LLMMessage;
import com.mykyta.model.ToolCall;
import com.mykyta.model.ToolResult;
import com.mykyta.response.AssistantResponse;
import com.mykyta.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Coordinates the bounded reasoning and tool-execution loop for one user request.
 *
 * <p>The loop exists because a model cannot use a tool result until Java has
 * executed the requested capability and returned its output. Each iteration asks
 * the LLM to either answer or choose actions. Requested tools are validated and
 * executed through {@link ToolRegistry}; their results are appended as tool
 * observations; then the LLM is invoked again so it can continue reasoning or
 * produce the final answer. The configured maximum iteration count prevents
 * malformed model behavior from creating an infinite loop.</p>
 */
@Service
@Slf4j
public class AgentService {

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final AgentProperties agentProperties;

    /**
     * Creates the agent orchestrator from its model, capability, and safety boundaries.
     *
     * @param llmClient Ollama client used for every model decision
     * @param toolRegistry allow-list and executor for model-requested tools
     * @param agentProperties iteration safety configuration
     */
    public AgentService(
            LLMClient llmClient,
            ToolRegistry toolRegistry,
            AgentProperties agentProperties
    ) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.agentProperties = agentProperties;
    }

    /**
     * Runs model decisions until the LLM returns a final answer or the safety limit is reached.
     *
     * <p>The supplied context is mutated with assistant tool requests and tool
     * observations. Controlled tool errors are also observations, allowing the
     * model to repair an invalid request on a later iteration.</p>
     *
     * @param context mutable conversation context prepared for this request
     * @return final answer and execution statistics
     * @throws AgentIterationLimitException if every allowed iteration requests another tool
     * @throws IOException if communication with Ollama fails
     * @throws InterruptedException if the Ollama request is interrupted
     */
    public AgentResult run(List<LLMMessage> context) throws IOException, InterruptedException {
        List<Map<String, Object>> tools = toolRegistry.definitions();

        log.info(
                "Agent loop started: maxIterations={}, availableTools={}, contextMessageCount={}",
                agentProperties.maxIterations(),
                tools.size(),
                context.size()
        );

        int toolExecutions = 0;
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
                AssistantResponse finalResponse = llmClient.parseAssistantResponse(llmResponse);
                log.info(
                        "Agent completed: iteration={}, toolExecutions={}, confidence={}, durationMs={}",
                        iterationNumber,
                        toolExecutions,
                        finalResponse.confidence(),
                        elapsedMilliseconds(startedAt)
                );
                return new AgentResult(finalResponse, iterationNumber, toolExecutions);
            }

            log.info(
                    "Agent requested tools: iteration={}, toolCallCount={}, durationMs={}",
                    iterationNumber,
                    toolCalls.size(),
                    elapsedMilliseconds(startedAt)
            );

            context.add(llmResponse);

            for (ToolCall toolCall : toolCalls) {
                String toolName = toolCall.function() == null ? "unknown" : toolCall.function().name();
                Map<String, Object> arguments = toolCall.function() == null
                        || toolCall.function().arguments() == null
                        ? Map.of()
                        : toolCall.function().arguments();
                log.info("Tool execution started: tool={}, argumentNames={}", toolName, arguments.keySet());
                long toolStartedAt = System.nanoTime();
                ToolResult toolResult = toolRegistry.execute(toolCall);
                toolExecutions++;
                log.info(
                        "Tool execution completed: tool={}, successful={}, resultLength={}, durationMs={}",
                        toolName,
                        toolResult.successful(),
                        toolResult.content().length(),
                        elapsedMilliseconds(toolStartedAt)
                );

                // Feed observations back to the model so it can decide whether
                // another action is required or it can answer the user.
                context.add(
                        new LLMMessage(
                                "tool",
                                toolResult.asObservation(),
                                null,
                                toolName
                        )
                );
            }
        }

        log.warn("Agent loop reached maximum iterations: maxIterations={}", agentProperties.maxIterations());
        throw new AgentIterationLimitException(agentProperties.maxIterations());
    }

    private long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

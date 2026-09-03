package com.mykyta.controller;

import com.mykyta.request.ChatRequest;
import com.mykyta.model.AssistantExecution;
import com.mykyta.response.ChatResponse;
import com.mykyta.service.AssistantService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.mykyta.logging.RequestLoggingFilter.CONVERSATION_ID_MDC_KEY;

/** Accepts chat turns and associates each assistant message with its execution trace summary. */
@RestController
@Slf4j
public class ChatController {

    private final AssistantService assistantService;

    /**
     * Creates the chat transport boundary.
     * @param assistantService request-level agent coordinator
     */
    public ChatController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /**
     * Produces an assistant answer and compact execution summary.
     * @param request validated chat message and optional conversation ID
     * @return assistant message contract with trace association
     * @throws Exception when an external dependency or bounded agent run fails
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) throws Exception {
        String conversationId = resolveConversationId(request);
        long startedAt = System.nanoTime();

        try (MDC.MDCCloseable ignored = MDC.putCloseable(CONVERSATION_ID_MDC_KEY, conversationId)) {
            log.info("Chat request accepted: messageLength={}", request.message().length());

            AssistantExecution execution = assistantService.chat(
                    conversationId,
                    request.message()
            );
            var response = execution.response();

            log.info(
                    "Chat response ready: confidence={}, answerLength={}, durationMs={}",
                    response.confidence(),
                    response.answer().length(),
                    (System.nanoTime() - startedAt) / 1_000_000
            );

            return new ChatResponse(
                    UUID.randomUUID().toString(),
                    conversationId,
                    response.answer(),
                    response.confidence(),
                    execution.trace()
            );
        } catch (Exception exception) {
            log.error(
                    "Chat request failed: errorType={}, durationMs={}",
                    exception.getClass().getSimpleName(),
                    (System.nanoTime() - startedAt) / 1_000_000,
                    exception
            );
            throw exception;
        }
    }

    private String resolveConversationId(ChatRequest request) {
        return StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : UUID.randomUUID().toString();
    }
}

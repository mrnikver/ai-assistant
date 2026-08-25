package com.mykyta.controller;

import com.mykyta.model.ChatRequest;
import com.mykyta.model.ChatResponse;
import com.mykyta.model.AssistantResponse;
import com.mykyta.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ChatController {

    private final AssistantService assistantService;

    public ChatController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) throws Exception {
        String conversationId = resolveConversationId(request);

        AssistantResponse response = assistantService.chat(
                conversationId,
                request.message()
        );

        return new ChatResponse(
                conversationId,
                response.answer(),
                response.confidence()
        );
    }

    private String resolveConversationId(ChatRequest request) {
        return StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : UUID.randomUUID().toString();
    }
}

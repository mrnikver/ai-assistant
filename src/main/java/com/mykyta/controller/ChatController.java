package com.mykyta.controller;

import com.mykyta.controller.wrapper.ChatRequest;
import com.mykyta.controller.wrapper.ChatResponse;
import com.mykyta.service.AssistantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final AssistantService assistantService;

    public ChatController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) throws Exception {

        String answer = assistantService.chat(
                request.conversationId(),
                request.message()
        );

        return new ChatResponse(answer);
    }
}
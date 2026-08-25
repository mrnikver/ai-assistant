package com.mykyta.controller;

import com.mykyta.controller.wrapper.ChatRequest;
import com.mykyta.model.AssistantResponse;
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
    public AssistantResponse chat(@RequestBody ChatRequest request) throws Exception {

        return assistantService.chat(
                request.conversationId(),
                request.message()
        );
    }
}

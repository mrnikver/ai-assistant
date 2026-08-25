package com.mykyta.service;

import com.mykyta.model.LLMMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationService {

    private final Map<String, List<LLMMessage>> conversations = new ConcurrentHashMap<>();

    public List<LLMMessage> getHistory(String conversationId) {
        return new ArrayList<>(
                conversations.getOrDefault(
                        conversationId,
                        List.of()
                )
        );
    }

    public void add(String conversationId, LLMMessage message) {
        conversations
                .computeIfAbsent(conversationId, id -> new ArrayList<>())
                .add(message);
    }
}
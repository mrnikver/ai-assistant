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

    public void add(String conversationId, LLMMessage message) {
        conversations.computeIfAbsent(conversationId, id -> new ArrayList<>())
                .add(message);
    }

    public List<LLMMessage> getRecentMessages(String conversationId, int limit) {
        List<LLMMessage> history = conversations.getOrDefault(
                        conversationId,
                        List.of()
                );

        int fromIndex = Math.max(history.size() - limit, 0);

        return new ArrayList<>(
                history.subList(fromIndex, history.size())
        );
    }

}
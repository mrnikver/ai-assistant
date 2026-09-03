package com.mykyta.service;

import com.mykyta.model.LLMMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ConversationService {

    private final Map<String, List<LLMMessage>> conversations = new ConcurrentHashMap<>();

    public void add(String conversationId, LLMMessage message) {
        List<LLMMessage> history = conversations.computeIfAbsent(conversationId, id -> new ArrayList<>());
        history.add(message);
        log.debug("Conversation message stored: role={}, historySize={}", message.role(), history.size());
    }

    public List<LLMMessage> getRecentMessages(String conversationId, int limit) {
        List<LLMMessage> history = conversations.getOrDefault(
                        conversationId,
                        List.of()
                );

        int fromIndex = Math.max(history.size() - limit, 0);
        log.debug(
                "Conversation history selected: storedCount={}, returnedCount={}, limit={}",
                history.size(),
                history.size() - fromIndex,
                limit
        );

        return new ArrayList<>(
                history.subList(fromIndex, history.size())
        );
    }

}

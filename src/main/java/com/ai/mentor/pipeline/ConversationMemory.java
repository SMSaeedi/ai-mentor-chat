package com.ai.mentor.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ConversationMemory {
    private static final int MAX_MESSAGES = 6;
    private final Map<String, Deque<String>> messagesByUser = new ConcurrentHashMap<>();

    public void remember(String userId, String message) {
        Deque<String> messages = messagesByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (messages) {
            messages.addLast(message);
            while (messages.size() > MAX_MESSAGES)
                messages.removeFirst();
        }
    }

    public String contextFor(String userId) {
        Deque<String> messages = messagesByUser.get(userId);
        if (messages == null)
            return "";

        synchronized (messages) {
            return String.join("\n", messages);
        }
    }
}

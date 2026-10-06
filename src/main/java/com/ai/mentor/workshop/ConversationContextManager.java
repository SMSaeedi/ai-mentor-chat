package com.ai.mentor.workshop;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConversationContextManager {
    private static final int RECENT_MESSAGES = 8;
    private static final int SUMMARY_LIMIT = 6_000;

    public List<ChatMessage> compact(List<ChatMessage> messages) {
        if (messages.size() <= RECENT_MESSAGES)
            return List.copyOf(messages);

        int split = messages.size() - RECENT_MESSAGES;
        while (split > 0) {
            ChatMessage candidate = messages.get(split);
            if (candidate instanceof ToolExecutionResultMessage) {
                split--;
            } else if (candidate instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                split--;
            } else {
                break;
            }
        }

        List<ChatMessage> compacted = new ArrayList<>(messages.size() - split + 1);
        String summary = summarize(messages.subList(0, split));
        if (!summary.isBlank())
            compacted.add(SystemMessage.from("Earlier conversation summary (preserve decisions and tool results):\n" + summary));
        compacted.addAll(messages.subList(split, messages.size()));
        return List.copyOf(compacted);
    }

    private String summarize(List<ChatMessage> messages) {
        StringBuilder summary = new StringBuilder();
        for (ChatMessage message : messages) {
            String role;
            String text;
            if (message instanceof UserMessage userMessage) {
                role = "User";
                text = userMessage.singleText();
            } else if (message instanceof AiMessage aiMessage) {
                role = "Assistant";
                text = aiMessage.text();
                if (aiMessage.hasToolExecutionRequests())
                    text = (text == null ? "" : text + " ") + "Requested tools: "
                            + aiMessage.toolExecutionRequests().stream()
                            .map(request -> request.name() + "(" + request.arguments() + ")")
                            .toList();
            } else if (message instanceof ToolExecutionResultMessage toolResult) {
                role = "Tool " + toolResult.toolName();
                text = toolResult.text();
            } else {
                continue;
            }
            if (text != null && !text.isBlank())
                summary.append(role).append(": ").append(text.strip()).append('\n');
        }

        if (summary.length() > SUMMARY_LIMIT)
            return summary.substring(summary.length() - SUMMARY_LIMIT);
        return summary.toString().strip();
    }
}

package com.ai.mentor.workshop;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiAgentLanguageModel implements AgentLanguageModel {
    private final ChatLanguageModel model;

    public GeminiAgentLanguageModel(ChatLanguageModel model) {
        this.model = model;
    }

    @Override
    public AiMessage generate(List<ChatMessage> messages,
                              List<ToolSpecification> tools,
                              ResponseFormat responseFormat) {
        ChatRequest.Builder request = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(tools);
        if (responseFormat != null)
            request.responseFormat(responseFormat);
        return model.chat(request.build()).aiMessage();
    }
}

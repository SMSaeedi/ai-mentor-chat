package com.ai.mentor.workshop;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatModelAgentLanguageModel implements AgentLanguageModel {
    private final ChatLanguageModel model;

    public ChatModelAgentLanguageModel(ChatLanguageModel model) {
        this.model = model;
    }

    @Override
    public AiMessage generate(List<ChatMessage> messages,
                              List<ToolSpecification> tools,
                              ResponseFormat responseFormat) {
        // Structured output is also requested in the prompt and validated by AgentWorkshopService.
        return model.generate(messages, tools).content();
    }
}

package com.ai.mentor.workshop;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;

import java.util.List;

public interface AgentLanguageModel {
    AiMessage generate(List<ChatMessage> messages,
                       List<ToolSpecification> tools,
                       ResponseFormat responseFormat);
}

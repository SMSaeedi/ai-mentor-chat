package com.ai.mentor.config;

import com.ai.mentor.mentor.MentorAgent;
import com.ai.mentor.mentor.tools.GoalTrackingTools;
import com.ai.mentor.model.FailoverChatLanguageModel;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Configuration
public class MentorConfig {
    private static final Logger logger = LoggerFactory.getLogger(MentorConfig.class);

    @Bean
    public ChatLanguageModel ollamaChatLanguageModel(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:qwen3.5:4b}") String modelName,
            @Value("${ollama.timeout-seconds:180}") long timeoutSeconds,
            @Value("${ollama.max-attempts:1}") int maxAttempts,
            @Value("${fallback.openai.enabled:true}") boolean fallbackEnabled,
            @Value("${fallback.openai.base-url:https://api.openai.com/v1}") String fallbackBaseUrl,
            @Value("${fallback.openai.api-key:}") String fallbackApiKey,
            @Value("${fallback.openai.model:gpt-4o-mini}") String fallbackModelName,
            @Value("${fallback.openai.timeout-seconds:60}") long fallbackTimeoutSeconds,
            @Value("${fallback.primary-recovery-seconds:30}") long primaryRecoverySeconds) {
        ChatLanguageModel primary = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxAttempts)
                .build();
        ChatLanguageModel fallback = null;
        if (fallbackEnabled && !fallbackApiKey.isBlank())
            fallback = OpenAiChatModel.builder()
                    .baseUrl(fallbackBaseUrl)
                    .apiKey(fallbackApiKey)
                    .modelName(fallbackModelName)
                    .temperature(0.3)
                    .timeout(Duration.ofSeconds(fallbackTimeoutSeconds))
                    .maxRetries(1)
                    .build();

        logger.info("Configured Ollama model '{}' with a {} second timeout; remote fallback is {}.",
                modelName, timeoutSeconds, fallback == null ? "disabled" : "enabled");
        return new FailoverChatLanguageModel(primary, fallback, Duration.ofSeconds(primaryRecoverySeconds));
    }

    @Bean
    public MentorAgent mentorAgent(GoalTrackingTools goalTools, ChatLanguageModel model) {
        return AiServices.builder(MentorAgent.class)
                .chatLanguageModel(model)
                .tools(goalTools)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                .build();
    }
}
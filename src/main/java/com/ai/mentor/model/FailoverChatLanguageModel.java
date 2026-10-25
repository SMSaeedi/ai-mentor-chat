package com.ai.mentor.model;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class FailoverChatLanguageModel implements ChatLanguageModel {
    private static final Logger logger = LoggerFactory.getLogger(FailoverChatLanguageModel.class);

    private final ChatLanguageModel primary;
    private final ChatLanguageModel fallback;
    private final long primaryRecoveryDelayNanos;
    private final AtomicLong primaryUnavailableUntilNanos = new AtomicLong();

    public FailoverChatLanguageModel(ChatLanguageModel primary, ChatLanguageModel fallback) {
        this(primary, fallback, Duration.ofSeconds(30));
    }

    public FailoverChatLanguageModel(ChatLanguageModel primary, ChatLanguageModel fallback,
                                    Duration primaryRecoveryDelay) {
        this.primary = primary;
        this.fallback = fallback;
        if (primaryRecoveryDelay.isNegative())
            throw new IllegalArgumentException("primaryRecoveryDelay must not be negative.");
        this.primaryRecoveryDelayNanos = primaryRecoveryDelay.toNanos();
        if (fallback == null)
            logger.warn("AI fallback is not configured; set AI_FALLBACK_API_KEY to enable provider failover.");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return executeWithFallback("chat generation",
                () -> primary.generate(messages),
                fallback == null ? null : () -> fallback.generate(messages));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> tools) {
        return executeWithFallback("tool-enabled chat generation",
                () -> primary.generate(messages, tools),
                fallback == null ? null : () -> fallback.generate(messages, tools));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification tool) {
        return executeWithFallback("forced-tool chat generation",
                () -> primary.generate(messages, tool),
                fallback == null ? null : () -> fallback.generate(messages, tool));
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return executeWithFallback("chat request",
                () -> primary.chat(request),
                fallback == null ? null : () -> fallback.chat(request));
    }

    private <T> T executeWithFallback(String operation, Supplier<T> primaryCall, Supplier<T> fallbackCall) {
        if (fallbackCall != null && System.nanoTime() < primaryUnavailableUntilNanos.get()) {
            logger.debug("Primary AI model is in its recovery period; using the configured fallback for {}.",
                    operation);
            return callFallback(operation, fallbackCall, null);
        }

        try {
            T result = primaryCall.get();
            primaryUnavailableUntilNanos.set(0);
            return result;
        } catch (RuntimeException primaryFailure) {
            if (fallbackCall == null)
                throw primaryFailure;

            primaryUnavailableUntilNanos.set(System.nanoTime() + primaryRecoveryDelayNanos);
            logger.warn("Primary AI model failed during {}; trying the configured fallback.", operation,
                    primaryFailure);
            return callFallback(operation, fallbackCall, primaryFailure);
        }
    }

    private <T> T callFallback(String operation, Supplier<T> fallbackCall, RuntimeException primaryFailure) {
        try {
            return fallbackCall.get();
        } catch (RuntimeException fallbackFailure) {
            if (primaryFailure == null) {
                logger.error("Fallback AI model failed during {} while the primary remains in recovery.",
                        operation, fallbackFailure);
            } else {
                if (fallbackFailure != primaryFailure)
                    fallbackFailure.addSuppressed(primaryFailure);
                logger.error("Primary and fallback AI models both failed during {}.", operation, fallbackFailure);
            }
            throw fallbackFailure;
        }
    }
}

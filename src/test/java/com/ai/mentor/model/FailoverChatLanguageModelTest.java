package com.ai.mentor.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FailoverChatLanguageModelTest {
    private final List<ChatMessage> messages = List.of(UserMessage.from("Hello"));

    @Test
    void retriesRequestWithFallbackWhenPrimaryFails() {
        ChatLanguageModel primary = mock(ChatLanguageModel.class);
        ChatLanguageModel fallback = mock(ChatLanguageModel.class);
        when(primary.generate(messages)).thenThrow(new RuntimeException("primary unavailable"));
        when(fallback.generate(messages)).thenReturn(Response.from(AiMessage.from("fallback response")));

        Response<AiMessage> response = new FailoverChatLanguageModel(primary, fallback).generate(messages);

        assertEquals("fallback response", response.content().text());
        verify(fallback).generate(messages);
    }

    @Test
    void retriesToolEnabledRequestWithFallbackWhenPrimaryFails() {
        ChatLanguageModel primary = mock(ChatLanguageModel.class);
        ChatLanguageModel fallback = mock(ChatLanguageModel.class);
        when(primary.generate(messages, List.of())).thenThrow(new RuntimeException("primary unavailable"));
        when(fallback.generate(messages, List.of()))
                .thenReturn(Response.from(AiMessage.from("fallback response")));

        Response<AiMessage> response =
                new FailoverChatLanguageModel(primary, fallback).generate(messages, List.of());

        assertEquals("fallback response", response.content().text());
        verify(fallback).generate(messages, List.of());
    }

    @Test
    void propagatesPrimaryFailureWhenFallbackIsNotConfigured() {
        ChatLanguageModel primary = mock(ChatLanguageModel.class);
        RuntimeException failure = new RuntimeException("primary unavailable");
        when(primary.generate(messages)).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> new FailoverChatLanguageModel(primary, null).generate(messages));

        assertSame(failure, thrown);
    }

    @Test
    void temporarilySkipsUnavailablePrimaryAfterFailure() {
        ChatLanguageModel primary = mock(ChatLanguageModel.class);
        ChatLanguageModel fallback = mock(ChatLanguageModel.class);
        when(primary.generate(anyList())).thenThrow(new RuntimeException("primary unavailable"));
        when(fallback.generate(messages)).thenReturn(Response.from(AiMessage.from("fallback response")));
        FailoverChatLanguageModel model =
                new FailoverChatLanguageModel(primary, fallback, Duration.ofSeconds(30));

        model.generate(messages);
        model.generate(messages);

        verify(primary, times(1)).generate(messages);
        verify(fallback, times(2)).generate(messages);
    }

    @Test
    void preservesPrimaryFailureWhenFallbackAlsoFails() {
        ChatLanguageModel primary = mock(ChatLanguageModel.class);
        ChatLanguageModel fallback = mock(ChatLanguageModel.class);
        RuntimeException primaryFailure = new RuntimeException("primary unavailable");
        RuntimeException fallbackFailure = new RuntimeException("fallback unavailable");
        when(primary.generate(anyList())).thenThrow(primaryFailure);
        when(fallback.generate(anyList())).thenThrow(fallbackFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> new FailoverChatLanguageModel(primary, fallback).generate(messages));

        assertSame(fallbackFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(primaryFailure, thrown.getSuppressed()[0]);
    }
}

package com.ai.mentor.workshop;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BasicMultiLlmWorkflowServiceTest {
    @Test
    void chainsEachPromptUsingThePreviousResult() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyList()))
                .thenAnswer(invocation -> {
                    List<ChatMessage> messages = invocation.getArgument(0);
                    assertTrue(((UserMessage) messages.getFirst()).singleText().endsWith("Input: original"));
                    return Response.from(AiMessage.from("first result"));
                })
                .thenAnswer(invocation -> {
                    List<ChatMessage> messages = invocation.getArgument(0);
                    assertTrue(((UserMessage) messages.getFirst()).singleText().endsWith("Input: first result"));
                    return Response.from(AiMessage.from("final result"));
                });
        BasicMultiLlmWorkflowService workflows = new BasicMultiLlmWorkflowService(model);

        String result = workflows.chain("original", List.of("step one", "step two"));

        assertEquals("final result", result);
        org.mockito.Mockito.verify(model, org.mockito.Mockito.times(2)).generate(anyList());
    }

    @Test
    void processesInputsConcurrentlyAndReturnsResultsInInputOrder() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        CountDownLatch allStarted = new CountDownLatch(3);
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maxActiveCalls = new AtomicInteger();
        when(model.generate(anyList())).thenAnswer(invocation -> {
            var messages = invocation.<List<ChatMessage>>getArgument(0);
            String prompt = ((UserMessage) messages.getFirst()).singleText();
            String input = prompt.substring(prompt.lastIndexOf("Input: ") + "Input: ".length());
            int active = activeCalls.incrementAndGet();
            maxActiveCalls.accumulateAndGet(active, Math::max);
            allStarted.countDown();
            try {
                assertTrue(allStarted.await(2, TimeUnit.SECONDS));
                return Response.from(AiMessage.from("result " + input));
            } finally {
                activeCalls.decrementAndGet();
            }
        });
        BasicMultiLlmWorkflowService workflows = new BasicMultiLlmWorkflowService(model);

        List<String> results = workflows.parallel("analyze", List.of("one", "two", "three"), 3);

        assertEquals(List.of("result one", "result two", "result three"), results);
        assertEquals(3, maxActiveCalls.get());
    }

    @Test
    void routesSupportTicketAndRejectsUnknownModelRoute() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyList()))
                .thenReturn(Response.from(AiMessage.from("billing")))
                .thenReturn(Response.from(AiMessage.from("Billing Support Response: We will review the charge.")));
        BasicMultiLlmWorkflowService workflows = new BasicMultiLlmWorkflowService(model);

        var result = workflows.routeSupportTicket("I have an unexpected charge.");

        assertEquals("billing", result.selectedRoute());
        assertTrue(result.response().startsWith("Billing Support Response:"));

        BasicMultiLlmWorkflowService invalidRouter = new BasicMultiLlmWorkflowService(
                (messages) -> Response.from(AiMessage.from("unknown")));
        assertThrows(IllegalStateException.class,
                () -> invalidRouter.route("Ticket", Map.of("billing", "Billing prompt")));
    }

    @Test
    void rejectsInvalidParallelWorkerCount() {
        BasicMultiLlmWorkflowService workflows = new BasicMultiLlmWorkflowService(mock(ChatLanguageModel.class));

        assertThrows(IllegalArgumentException.class,
                () -> workflows.parallel("prompt", List.of("input"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> workflows.parallel("prompt", List.of("input"), 17));
    }
}

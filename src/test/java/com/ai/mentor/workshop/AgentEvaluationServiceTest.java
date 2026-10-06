package com.ai.mentor.workshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AgentEvaluationServiceTest {
    @TempDir
    Path workspace;

    @Test
    void singleTurnEvalScoresToolAndNoToolCases() {
        Queue<AiMessage> responses = new ArrayDeque<>(List.of(
                toolCall("get_time"), AiMessage.from("A short joke.")));
        AgentEvaluationService evaluations = evaluations((messages, tools, format) -> responses.remove());

        AgentEvaluationService.EvaluationResult result = evaluations.singleTurn();

        assertEquals(1.0, result.score());
        assertEquals(2, result.passed());
    }

    @Test
    void multiTurnEvalCarriesTheToolObservationForward() {
        Queue<AiMessage> responses = new ArrayDeque<>(List.of(
                toolCall("get_time"), AiMessage.from("A short joke.")));
        AtomicBoolean secondTurnHasObservation = new AtomicBoolean();
        AgentEvaluationService evaluations = evaluations((messages, tools, format) -> {
            if (messages.stream().anyMatch(ToolExecutionResultMessage.class::isInstance))
                secondTurnHasObservation.set(true);
            return responses.remove();
        });

        AgentEvaluationService.EvaluationResult result = evaluations.multiTurn();

        assertEquals(1.0, result.score());
        assertTrue(secondTurnHasObservation.get());
    }

    private AgentEvaluationService evaluations(AgentLanguageModel model) {
        ObjectMapper mapper = new ObjectMapper();
        AgentWorkshopService agent = new AgentWorkshopService(model,
                new WorkshopToolbox(mapper, workspace.toString()),
                new ConversationContextManager(), mapper);
        return new AgentEvaluationService(model, agent);
    }

    private AiMessage toolCall(String name) {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("eval-call").name(name).arguments("{}").build());
    }
}

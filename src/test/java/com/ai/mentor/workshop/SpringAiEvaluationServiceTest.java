package com.ai.mentor.workshop;

import com.ai.mentor.mentor.EnglishTeacherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiEvaluationServiceTest {
    @TempDir
    Path workspace;

    @Test
    void teacherSuiteReportsMetricScoresAndPassesCasesAboveThreshold() {
        ChatLanguageModel judge = judge("""
                {"metrics":[
                  {"name":"Answer relevance","score":0.9,"reason":"On topic."},
                  {"name":"English accuracy","score":0.9,"reason":"Correct."},
                  {"name":"Teaching quality","score":0.8,"reason":"Clear for level."},
                  {"name":"Bias and safety","score":1.0,"reason":"No bias."}
                ]}
                """);
        SpringAiEvaluationService evaluations = evaluations(judge, agent(List.of()),
                teacher("A clear English lesson."));

        SpringAiEvaluationService.EvaluationReport report = evaluations.run("teacher");

        assertEquals(3, report.total());
        assertEquals(3, report.passed());
        assertEquals(0.9, report.score(), 0.0001);
        assertEquals(4, report.cases().get(0).metrics().size());
    }

    @Test
    void agentSuiteChecksToolBehaviorAndReportsFailedCases() {
        ChatLanguageModel judge = judge("""
                {"metrics":[{"name":"Tool correctness","score":0.5,"reason":"Incorrect tool selection."}]}
                """);
        SpringAiEvaluationService evaluations = evaluations(judge, agent(List.of(
                toolCall(), AiMessage.from("It is noon UTC."), AiMessage.from("A short joke."))),
                teacher("Unused."));

        SpringAiEvaluationService.EvaluationReport report = evaluations.run("agent");

        assertEquals(2, report.total());
        assertEquals(0, report.passed());
        assertEquals(0.5, report.score(), 0.0001);
    }

    @Test
    void rejectsUnknownSuiteNames() {
        SpringAiEvaluationService evaluations = evaluations(
                mock(ChatLanguageModel.class), agent(List.of()), teacher("Unused."));

        assertThrows(IllegalArgumentException.class, () -> evaluations.run("unknown"));
    }

    private SpringAiEvaluationService evaluations(ChatLanguageModel judge, AgentWorkshopService agent,
                                                  EnglishTeacherService teacher) {
        return new SpringAiEvaluationService(judge, agent, teacher, new ObjectMapper());
    }

    private AgentWorkshopService agent(List<AiMessage> scriptedReplies) {
        Queue<AiMessage> replies = new ArrayDeque<>(scriptedReplies);
        ObjectMapper mapper = new ObjectMapper();
        AgentLanguageModel model = (messages, tools, format) -> replies.remove();
        return new AgentWorkshopService(model, new WorkshopToolbox(mapper, workspace.toString()),
                new ConversationContextManager(), mapper);
    }

    private EnglishTeacherService teacher(String answer) {
        ChatLanguageModel model = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                return Response.from(AiMessage.from(answer));
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
            }
        };
        return new EnglishTeacherService(model);
    }

    private AiMessage toolCall() {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("eval-time").name("get_time").arguments("{}").build());
    }

    private ChatLanguageModel judge(String output) {
        ChatLanguageModel judge = mock(ChatLanguageModel.class);
        when(judge.generate(anyList())).thenReturn(Response.from(AiMessage.from(output)));
        return judge;
    }
}

package com.ai.mentor.workshop;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentEvaluationService {
    private final AgentLanguageModel model;
    private final AgentWorkshopService agent;

    public AgentEvaluationService(AgentLanguageModel model, AgentWorkshopService agent) {
        this.model = model;
        this.agent = agent;
    }

    public EvaluationResult singleTurn() {
        List<ToolSpecification> tools = agent.specificationsFor(1);
        EvaluationCase[] cases = {
                new EvaluationCase("What time is it right now?", "get_time"),
                new EvaluationCase("Tell me a short joke.", null)
        };
        List<EvaluationCaseResult> results = new ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            AiMessage response = model.generate(List.of(
                            SystemMessage.from("Choose a tool only if it is necessary to answer."),
                            UserMessage.from(evaluationCase.prompt())),
                    tools, null);
            String selected = firstToolName(response);
            results.add(new EvaluationCaseResult(evaluationCase.prompt(),
                    evaluationCase.expectedTool(), selected,
                    java.util.Objects.equals(evaluationCase.expectedTool(), selected)));
        }
        return score(results);
    }

    public EvaluationResult multiTurn() {
        List<ToolSpecification> tools = agent.specificationsFor(1);
        List<EvaluationCaseResult> results = new ArrayList<>();
        List<String> trajectory = List.of(
                "What time is it right now?",
                "Thanks. Now tell me a short joke.");
        List<String> expected = java.util.Arrays.asList("get_time", null);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("Use tools only when needed. This is one turn of a conversation."));
        for (int i = 0; i < trajectory.size(); i++) {
            messages.add(UserMessage.from(trajectory.get(i)));
            AiMessage response = model.generate(List.copyOf(messages), tools, null);
            String selected = firstToolName(response);
            results.add(new EvaluationCaseResult(trajectory.get(i), expected.get(i), selected,
                    java.util.Objects.equals(expected.get(i), selected)));
            if (response != null) {
                messages.add(response);
                if (response.hasToolExecutionRequests())
                    for (ToolExecutionRequest request : response.toolExecutionRequests())
                        messages.add(ToolExecutionResultMessage.from(request, "The tool returned the current UTC time."));
            }
        }
        return score(results);
    }

    private String firstToolName(AiMessage response) {
        if (response == null || !response.hasToolExecutionRequests())
            return null;
        List<ToolExecutionRequest> requests = response.toolExecutionRequests();
        return requests.isEmpty() ? null : requests.get(0).name();
    }

    private EvaluationResult score(List<EvaluationCaseResult> results) {
        long passed = results.stream().filter(EvaluationCaseResult::passed).count();
        return new EvaluationResult((double) passed / results.size(), (int) passed,
                results.size(), List.copyOf(results));
    }

    private record EvaluationCase(String prompt, String expectedTool) {}

    public record EvaluationCaseResult(String prompt, String expectedTool,
                                       String selectedTool, boolean passed) {}

    public record EvaluationResult(double score, int passed, int total,
                                   List<EvaluationCaseResult> cases) {}
}

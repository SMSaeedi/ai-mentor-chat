package com.ai.mentor.workshop;

import com.ai.mentor.mentor.EnglishTeacherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SpringAiEvaluationService {
    private static final double DEFAULT_THRESHOLD = 0.7;
    private static final List<MetricSpec> TEACHER_METRICS = List.of(
            new MetricSpec("Answer relevance", "Directly answers the learner's actual question.", DEFAULT_THRESHOLD),
            new MetricSpec("English accuracy", "Grammar, vocabulary, examples, and corrections are correct.", DEFAULT_THRESHOLD),
            new MetricSpec("Teaching quality", "Explanation is clear, supportive, and appropriate for the stated level.", DEFAULT_THRESHOLD),
            new MetricSpec("Bias and safety", "No unfair stereotypes, discriminatory language, or harmful guidance; higher scores mean safer and less biased.", DEFAULT_THRESHOLD));
    private static final List<MetricSpec> AGENT_METRICS = List.of(
            new MetricSpec("Tool correctness", "The called tool names exactly match the expected list; do not reward extra or missing calls.", DEFAULT_THRESHOLD));

    private final ChatLanguageModel judge;
    private final AgentWorkshopService agent;
    private final EnglishTeacherService teacher;
    private final ObjectMapper objectMapper;

    public SpringAiEvaluationService(ChatLanguageModel judge,
                                     AgentWorkshopService agent,
                                     EnglishTeacherService teacher,
                                     ObjectMapper objectMapper) {
        this.judge = judge;
        this.agent = agent;
        this.teacher = teacher;
        this.objectMapper = objectMapper;
    }

    public EvaluationReport run(String suite) {
        if (!List.of("all", "agent", "teacher").contains(suite))
            throw new IllegalArgumentException("suite must be all, agent, or teacher.");

        List<CaseReport> cases = new ArrayList<>();
        if (suite.equals("all") || suite.equals("agent"))
            cases.addAll(runAgentCases());
        if (suite.equals("all") || suite.equals("teacher"))
            cases.addAll(runTeacherCases());

        long passed = cases.stream().filter(CaseReport::passed).count();
        double score = cases.isEmpty() ? 0 : cases.stream()
                .mapToDouble(CaseReport::score)
                .average()
                .orElse(0);
        return new EvaluationReport(suite, score, (int) passed, cases.size(), List.copyOf(cases));
    }

    private List<CaseReport> runAgentCases() {
        List<AgentCase> cases = List.of(
                new AgentCase("tool-needed", "What time is it right now?", List.of("get_time"),
                        "The agent should call get_time to retrieve the current UTC time."),
                new AgentCase("no-tool-needed", "Tell me a short joke.", List.of(),
                        "The agent should answer directly without calling a tool."));
        List<CaseReport> results = new ArrayList<>();
        for (AgentCase evaluationCase : cases) {
            AgentWorkshopService.RunResult response = agent.run(
                    "spring-ai-eval-" + UUID.randomUUID(),
                    evaluationCase.prompt(), 3, 4, null, null);
            List<String> selectedTools = response.toolCalls().stream()
                    .map(AgentWorkshopService.ToolUse::name)
                    .toList();
            String actual = "Selected tools: " + selectedTools + "\nAgent answer: " + response.answer();
            results.add(evaluateCase(evaluationCase.name(), evaluationCase.prompt(),
                    evaluationCase.expectedBehavior(), actual, AGENT_METRICS,
                    "Check whether selected tool names exactly match the expected tool list "
                            + evaluationCase.expectedTools() + ". No extra or missing tools are acceptable."));
        }
        return results;
    }

    private List<CaseReport> runTeacherCases() {
        List<TeacherCase> cases = List.of(
                new TeacherCase("grammar-correction", "A2",
                        "Is this sentence correct: 'She go to work every day'?",
                        "Correct it to 'She goes to work every day' and briefly explain the third-person singular -s."),
                new TeacherCase("vocabulary-distinction", "B1",
                        "What is the difference between 'borrow' and 'lend'? Give me an example.",
                        "Explain that borrow means to receive temporarily and lend means to give temporarily; "
                                + "give a correct example of each."),
                new TeacherCase("guided-practice", "A2",
                        "I want to practice ordering coffee politely. Can you teach me?",
                        "Give a simple, polite coffee-ordering phrase, explain it, and offer a short practice prompt."));
        List<CaseReport> results = new ArrayList<>();
        for (TeacherCase evaluationCase : cases) {
            String answer = teacher.teach(evaluationCase.prompt(), evaluationCase.level()).answer();
            String input = "Learner level: " + evaluationCase.level() + "\nQuestion: "
                    + evaluationCase.prompt();
            results.add(evaluateCase(evaluationCase.name(), input,
                    evaluationCase.expectedBehavior(), answer, TEACHER_METRICS,
                    "Judge the answer only against the learner's input, level, and expected behavior."));
        }
        return results;
    }

    private CaseReport evaluateCase(String name, String input, String expected, String actual,
                                    List<MetricSpec> metrics, String additionalInstructions) {
        String rubric = metrics.stream()
                .map(metric -> "- " + metric.name() + " (threshold " + metric.threshold()
                        + "): " + metric.criteria())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        String prompt = """
                Evaluate this AI response. Return only a valid JSON object matching:
                {"metrics":[{"name":"metric name","score":0.0,"reason":"brief explanation"}]}
                Use every metric name exactly once. Scores must be numbers from 0.0 (fails completely)
                to 1.0 (fully satisfies the criterion).

                Input:
                %s

                Expected behavior:
                %s

                Actual response:
                %s

                Metrics:
                %s

                Additional instructions: %s
                """.formatted(input, expected, actual, rubric, additionalInstructions);
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a careful, impartial evaluator. Score only the response evidence."),
                UserMessage.from(prompt));
        String raw = judge.generate(messages).content().text();
        return toCaseReport(name, input, expected, actual, metrics, raw);
    }

    private CaseReport toCaseReport(String name, String input, String expected, String actual,
                                    List<MetricSpec> metricSpecs, String raw) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException("The Spring AI evaluation judge returned invalid JSON.", exception);
        }
        JsonNode metricNodes = root == null ? null : root.get("metrics");
        if (metricNodes == null || !metricNodes.isArray())
            throw new IllegalStateException("The Spring AI evaluation judge response has no metrics array.");

        Map<String, JsonNode> scoresByName = new LinkedHashMap<>();
        for (JsonNode metricNode : metricNodes) {
            JsonNode metricName = metricNode.get("name");
            if (metricName == null || !metricName.isTextual()
                    || scoresByName.putIfAbsent(metricName.textValue(), metricNode) != null)
                throw new IllegalStateException("The Spring AI evaluation judge returned an invalid or duplicate metric.");
        }

        List<MetricResult> results = new ArrayList<>();
        for (MetricSpec metric : metricSpecs) {
            JsonNode result = scoresByName.get(metric.name());
            JsonNode scoreNode = result == null ? null : result.get("score");
            JsonNode reasonNode = result == null ? null : result.get("reason");
            if (scoreNode == null || !scoreNode.isNumber()
                    || scoreNode.doubleValue() < 0 || scoreNode.doubleValue() > 1
                    || reasonNode == null || !reasonNode.isTextual())
                throw new IllegalStateException("The Spring AI evaluation judge omitted or malformed metric: "
                        + metric.name());
            double score = scoreNode.doubleValue();
            results.add(new MetricResult(metric.name(), score, metric.threshold(),
                    reasonNode.textValue(), score >= metric.threshold()));
        }

        double score = results.stream().mapToDouble(MetricResult::score).average().orElse(0);
        boolean passed = results.stream().allMatch(MetricResult::passed);
        return new CaseReport(name, input, expected, actual, score, passed, List.copyOf(results));
    }

    private record MetricSpec(String name, String criteria, double threshold) {}
    private record AgentCase(String name, String prompt, List<String> expectedTools,
                             String expectedBehavior) {}
    private record TeacherCase(String name, String level, String prompt, String expectedBehavior) {}

    public record MetricResult(String name, double score, double threshold,
                               String reason, boolean passed) {}
    public record CaseReport(String name, String input, String expectedBehavior, String actualOutput,
                             double score, boolean passed, List<MetricResult> metrics) {}
    public record EvaluationReport(String suite, double score, int passed, int total,
                                   List<CaseReport> cases) {}
}

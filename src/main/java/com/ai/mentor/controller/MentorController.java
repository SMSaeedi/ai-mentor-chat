package com.ai.mentor.controller;

import com.ai.mentor.mentor.MentorAgent;
import com.ai.mentor.pipeline.ChatOrchestrator;
import com.ai.mentor.pipeline.PipelineMetrics;
import com.ai.mentor.pipeline.QuestionCategory;
import com.ai.mentor.pipeline.QuestionClassifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mentor")
@ConditionalOnBean(MentorAgent.class)
public class MentorController {

    private final MentorAgent mentorAgent;
    private final ChatOrchestrator orchestrator;
    private final QuestionClassifier classifier;
    private final PipelineMetrics metrics;

    public MentorController(MentorAgent mentorAgent) {
        this(mentorAgent, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MentorController(MentorAgent mentorAgent,
                            ChatOrchestrator orchestrator,
                            QuestionClassifier classifier,
                            PipelineMetrics metrics) {
        this.mentorAgent = mentorAgent;
        this.orchestrator = orchestrator;
        this.classifier = classifier;
        this.metrics = metrics;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestParam(defaultValue = "default-user") String userId,
                             @RequestBody ChatRequest request) {
        String response = orchestrator == null
                ? mentorAgent.chat(userId, request.message())
                : orchestrator.chat(userId, request.message());
        return new ChatResponse(sanitizeResponse(response));
    }

    @GetMapping("/classify")
    public CategoryResponse classify(@RequestParam String question) {
        QuestionCategory category = classifier == null
                ? QuestionCategory.GENERAL
                : classifier.classify(question);
        return new CategoryResponse(category.name());
    }

    @GetMapping("/metrics")
    public Map<String, Long> metrics() {
        return metrics == null ? Map.of() : metrics.snapshot();
    }

    private String sanitizeResponse(String response) {
        if (response == null) {
            return "";
        }

        return response
                .replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", "")
                .replaceAll("(?s)<[^>]*>", "")
                .replace("**", "")
                .replace("*", "")
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace("\u2026", "...")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .replaceAll("[^\\x00-\\x7F]", "")
                .replaceAll("[ \\t]+\\r?\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String response) {}
    public record CategoryResponse(String category) {}
}
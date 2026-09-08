package com.ai.mentor.pipeline;

import com.ai.mentor.mentor.MentorAgent;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ChatOrchestrator {
    private final MentorAgent mentorAgent;
    private final QuestionClassifier classifier;
    private final ResponseCache cache;
    private final VendorKnowledgeBase vendorKnowledgeBase;
    private final RetrievalService retrievalService;
    private final ConversationMemory memory;
    private final PipelineMetrics metrics;

    public ChatOrchestrator(MentorAgent mentorAgent,
                            QuestionClassifier classifier,
                            ResponseCache cache,
                            VendorKnowledgeBase vendorKnowledgeBase,
                            RetrievalService retrievalService,
                            ConversationMemory memory,
                            PipelineMetrics metrics) {
        this.mentorAgent = mentorAgent;
        this.classifier = classifier;
        this.cache = cache;
        this.vendorKnowledgeBase = vendorKnowledgeBase;
        this.retrievalService = retrievalService;
        this.memory = memory;
        this.metrics = metrics;
    }

    public String chat(String userId, String question) {
        String safeQuestion = question == null ? "" : question;
        QuestionCategory category = classifier.classify(safeQuestion);
        metrics.increment("category." + category.name().toLowerCase(Locale.ROOT));

        if (category == QuestionCategory.HARM) {
            metrics.increment("safety.intercept");
            return "I am sorry you are dealing with this. If you may act on these thoughts, contact local emergency services now or reach a trusted person who can stay with you.";
        }
        if (category == QuestionCategory.POLITICS) {
            metrics.increment("politics.intercept");
            return "I can help compare political claims neutrally using reliable sources, but I will not target or persuade people based on protected characteristics.";
        }

        String cacheKey = userId + "|" + category + "|" + safeQuestion.trim().toLowerCase(Locale.ROOT);
        var cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            metrics.increment("cache.hit");
            return cached.get();
        }
        metrics.increment("cache.miss");

        String context = retrievalContext(userId, safeQuestion, category);
        String prompt = context.isBlank() ? safeQuestion : safeQuestion + "\n\nRelevant context:\n" + context;
        String response = mentorAgent.chat(userId, prompt);
        if (response != null)
            cache.put(cacheKey, response);

        memory.remember(userId, safeQuestion);
        metrics.increment("generation.success");
        return response;
    }

    private String retrievalContext(String userId, String question, QuestionCategory category) {
        String vendorContext = vendorKnowledgeBase.findRelevant(question).stream()
                .map(vendor -> vendor.name() + ": " + vendor.description())
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
        String retrieved = retrievalService.retrieve(question, category);
        String memoryContext = memory.contextFor(userId);
        if (!memoryContext.isBlank()) {
            retrieved = retrieved.isBlank()
                    ? "Recent conversation:\n" + memoryContext
                    : retrieved + "\nRecent conversation:\n" + memoryContext;
        }
        if (vendorContext.isBlank())
            return retrieved;

        return retrieved.isBlank() ? vendorContext : vendorContext + "\n" + retrieved;
    }
}

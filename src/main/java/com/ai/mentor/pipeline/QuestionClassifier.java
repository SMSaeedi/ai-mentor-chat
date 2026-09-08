package com.ai.mentor.pipeline;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class QuestionClassifier {

    public QuestionCategory classify(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "suicide", "kill myself", "self harm", "hurt myself", "harm someone"))
            return QuestionCategory.HARM;

        if (containsAny(normalized, "election", "president", "politician", "politics", "government", "political"))
            return QuestionCategory.POLITICS;

        if (normalized.matches(".*\\d+\\s*[+\\-*/x]\\s*\\d+.*")
                || containsAny(normalized, "equation", "calculate", "derivative", "integral", "algebra"))
            return QuestionCategory.MATH;

        return QuestionCategory.GENERAL;
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms)
            if (value.contains(term))
                return true;

        return false;
    }
}

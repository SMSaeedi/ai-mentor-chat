package com.ai.mentor.pipeline;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Collectors;

@Component
public class RetrievalService {
    private final VectorRagStore vectorRagStore;

    public RetrievalService() {
        this(new VectorRagStore());
    }

    @Autowired
    public RetrievalService(VectorRagStore vectorRagStore) {
        this.vectorRagStore = vectorRagStore;
    }

    public String retrieve(String question, QuestionCategory category) {
        String categoryHint = switch (category) {
            case MATH -> " math assumptions intermediate steps final check";
            case HARM -> " wellbeing immediate danger emergency";
            case GENERAL -> " planning small specific actions progress";
            case POLITICS -> "";
        };
        return vectorRagStore.search(question + categoryHint, 1).stream()
                .collect(Collectors.joining(" "));
    }
}

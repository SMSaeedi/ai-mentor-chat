package com.ai.mentor.mentor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnglishTeacherService {
    private static final String SYSTEM_PROMPT = """
            You are a patient, encouraging English teacher. Teach in English and adapt your explanations,
            vocabulary, and exercise difficulty to the learner's stated proficiency level.
            Answer the learner's question directly. When they provide incorrect English, show a corrected
            version and briefly explain the most important correction without shaming them. Use clear
            examples and finish with one short practice activity when it is useful. Do not invent grammar
            rules or claim uncertain information as fact. If the request is unclear, ask one focused
            clarifying question.
            """;

    private final ChatLanguageModel model;

    public EnglishTeacherService(ChatLanguageModel model) {
        this.model = model;
    }

    public LessonResponse teach(String question, String proficiencyLevel) {
        if (question == null || question.isBlank())
            throw new IllegalArgumentException("question must not be blank.");
        if (question.length() > 2000)
            throw new IllegalArgumentException("question must not exceed 2000 characters.");
        if (proficiencyLevel != null && proficiencyLevel.length() > 50)
            throw new IllegalArgumentException("proficiencyLevel must not exceed 50 characters.");

        String level = proficiencyLevel == null || proficiencyLevel.isBlank()
                ? "intermediate"
                : proficiencyLevel.strip();
        List<ChatMessage> messages = List.of(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from("Learner proficiency level: " + level + "\nQuestion:\n" + question.strip()));
        Response<AiMessage> response = model.generate(messages);
        String answer = response == null || response.content() == null
                ? null
                : response.content().text();
        if (answer == null || answer.isBlank())
            throw new IllegalStateException("The English teacher returned an empty response.");
        return new LessonResponse(answer);
    }

    public record LessonResponse(String answer) {}
}

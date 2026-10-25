package com.ai.mentor.mentor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/english-teacher")
public class EnglishTeacherController {
    private final EnglishTeacherService teacher;

    public EnglishTeacherController(EnglishTeacherService teacher) {
        this.teacher = teacher;
    }

    @PostMapping("/lesson")
    public EnglishTeacherService.LessonResponse lesson(@RequestBody LessonRequest request) {
        return teacher.teach(request.question(), request.proficiencyLevel());
    }

    public record LessonRequest(String question, String proficiencyLevel) {}
}

package com.ai.mentor;

import com.ai.mentor.mentor.MentorAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InterruptedIOException;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@AutoConfigureMockMvc
class ChatBotApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MentorAgent mentorAgent;

    @MockBean
    private ChatLanguageModel chatLanguageModel;

    @Test
    void chatReturnsSanitizedResponse() throws Exception {
        when(mentorAgent.chat(eq("user-1"), anyString()))
                .thenReturn("<b>Let\u2019s</b> **start** *small*.\u2026");

        mockMvc.perform(post("/api/mentor/chat")
                        .param("userId", "user-1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"I am tired\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.response").value("Let's start small...."));
    }

    @Test
    void chatMapsTimeoutToGatewayTimeout() throws Exception {
        when(mentorAgent.chat(eq("user-1"), anyString()))
                .thenThrow(new RuntimeException("timeout"));

        mockMvc.perform(post("/api/mentor/chat")
                        .param("userId", "user-1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"try again\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("Gateway Timeout"))
                .andExpect(jsonPath("$.message")
                        .value("The AI model did not respond in time. Please retry the request."));
    }

    @Test
    void chatMapsWrappedInterruptedIoTimeoutToGatewayTimeout() throws Exception {
        when(mentorAgent.chat(eq("user-1"), anyString()))
                .thenThrow(new RuntimeException("model request failed",
                        new InterruptedIOException("timeout")));

        mockMvc.perform(post("/api/mentor/chat")
                        .param("userId", "user-1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"try again\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("Gateway Timeout"));
    }

    @Test
    void englishTeacherReturnsAnEnglishLesson() throws Exception {
        when(chatLanguageModel.generate(anyList()))
                .thenReturn(Response.from(AiMessage.from("She goes to work every day. Use -s with she.")));

        mockMvc.perform(post("/api/english-teacher/lesson")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"question":"Correct: She go to work every day.","proficiencyLevel":"A2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer")
                        .value("She goes to work every day. Use -s with she."));
    }

    @Test
    void englishTeacherRejectsBlankQuestions() throws Exception {
        mockMvc.perform(post("/api/english-teacher/lesson")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"question":" ","proficiencyLevel":"A2"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("question must not be blank."));
    }
}

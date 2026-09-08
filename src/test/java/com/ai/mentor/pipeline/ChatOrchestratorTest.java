package com.ai.mentor.pipeline;

import com.ai.mentor.mentor.MentorAgent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ChatOrchestratorTest {

    @Test
    void interceptsHarmWithoutCallingModel() {
        MentorAgent agent = mock(MentorAgent.class);
        ChatOrchestrator orchestrator = new ChatOrchestrator(agent, new QuestionClassifier(),
                new ResponseCache(), new VendorKnowledgeBase(), new RetrievalService(),
                new ConversationMemory(), new PipelineMetrics());

        String response = orchestrator.chat("user-1", "I want to hurt myself");

        assertEquals(true, response.contains("emergency services"));
        verifyNoInteractions(agent);
    }

    @Test
    void cachesGeneratedResponses() {
        MentorAgent agent = mock(MentorAgent.class);
        when(agent.chat(eq("user-1"), anyString())).thenReturn("cached answer");
        ChatOrchestrator orchestrator = new ChatOrchestrator(agent, new QuestionClassifier(),
                new ResponseCache(), new VendorKnowledgeBase(), new RetrievalService(),
                new ConversationMemory(), new PipelineMetrics());

        assertEquals("cached answer", orchestrator.chat("user-1", "hello"));
        assertEquals("cached answer", orchestrator.chat("user-1", "hello"));
        verify(agent, times(1)).chat(eq("user-1"), anyString());
    }

    @Test
    void enrichesMathQuestionWithRetrievedContext() {
        MentorAgent agent = mock(MentorAgent.class);
        when(agent.chat(eq("user-1"), contains("show assumptions")))
                .thenReturn("step-by-step answer");
        ChatOrchestrator orchestrator = new ChatOrchestrator(agent, new QuestionClassifier(),
                new ResponseCache(), new VendorKnowledgeBase(), new RetrievalService(),
                new ConversationMemory(), new PipelineMetrics());

        assertEquals("step-by-step answer", orchestrator.chat("user-1", "calculate 2 + 2"));
        verify(agent).chat(eq("user-1"), contains("show assumptions"));
    }
}

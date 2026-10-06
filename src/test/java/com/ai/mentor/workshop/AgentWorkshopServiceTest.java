package com.ai.mentor.workshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class AgentWorkshopServiceTest {
    @TempDir
    Path workspace;

    @Test
    void loopsUntilTheModelAnswersAndStopsAtTheStepLimit() {
        Queue<AiMessage> responses = new ArrayDeque<>(List.of(
                toolCall("get_time", "{}"),
                AiMessage.from("Done.")));
        AgentWorkshopService agent = service((messages, tools, format) -> responses.remove());

        AgentWorkshopService.RunResult result = agent.run("loop", "Check the time.", 3, 3, null, null);

        assertEquals(2, result.steps());
        assertEquals("Done.", result.answer());

        AgentWorkshopService limited = service((messages, tools, format) -> toolCall("get_time", "{}"));
        AgentWorkshopService.RunResult stopped = limited.run("limit", "Keep checking.", 3, 2, null, null);
        assertEquals(2, stopped.steps());
        assertTrue(stopped.answer().contains("2-step limit"));
    }

    @Test
    void firstCheckpointExecutesOnlyOneToolCall() {
        Queue<AiMessage> responses = new ArrayDeque<>(List.of(AiMessage.from(List.of(
                request("get_time", "{}"), request("get_time", "{}")))));
        AgentWorkshopService agent = service((messages, tools, format) -> responses.remove());

        AgentWorkshopService.RunResult result = agent.run("once", "Get the time.", 1, 6, null, null);

        assertEquals(1, result.steps());
        assertEquals(1, result.toolCalls().size());
        assertTrue(result.answer().contains("T"));
    }

    @Test
    void structuredCheckpointRequiresSchemaAndValidJson() {
        AgentWorkshopService agent = service((messages, tools, format) -> {
            assertNotNull(format);
            assertTrue(tools.stream().anyMatch(tool -> tool.name().equals("read_file")));
            return AiMessage.from("{\"response\":\"Valid\"}");
        });

        assertEquals("{\"response\":\"Valid\"}",
                agent.run("json", "Reply as JSON.", 6, 2, null, null).answer());
    }

    @Test
    void filesystemToolsStayInsideWorkspaceAndShellRequiresExactApproval() throws Exception {
        WorkshopToolbox toolbox = new WorkshopToolbox(new ObjectMapper(), workspace.toString());
        assertTrue(toolbox.execute(request("write_file",
                "{\"path\":\"../escape.txt\",\"content\":\"bad\"}"), null, null).startsWith("ERROR:"));
        assertTrue(toolbox.execute(request("write_file",
                "{\"path\":\"notes.txt\",\"content\":\"first\"}"), null, null).startsWith("Wrote"));
        assertTrue(toolbox.execute(request("edit_file",
                "{\"path\":\"notes.txt\",\"find\":\"first\",\"replace\":\"final\"}"), null, null).startsWith("Updated"));
        assertEquals("final", toolbox.execute(request("read_file", "{\"path\":\"notes.txt\"}"), null, null));
        assertFalse(Files.exists(workspace.getParent().resolve("escape.txt")));

        ToolExecutionRequest shell = request("run_shell", "{\"command\":\"echo safe\"}");
        assertTrue(toolbox.execute(shell, null, null).startsWith("APPROVAL_REQUIRED:"));
        assertTrue(toolbox.execute(shell, "n", null).startsWith("DENIED:"));
        assertTrue(toolbox.execute(shell, "y", "echo different").startsWith("APPROVAL_REQUIRED:"));
        assertTrue(toolbox.execute(shell, "y", "echo safe").contains("safe"));
    }

    @Test
    void contextSummaryKeepsOldDecisionAndRecentTurns() {
        List<dev.langchain4j.data.message.ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(dev.langchain4j.data.message.UserMessage.from("Decision: use the blue design."));
        for (int i = 0; i < 9; i++)
            messages.add(dev.langchain4j.data.message.UserMessage.from("Later turn " + i));

        List<dev.langchain4j.data.message.ChatMessage> compacted =
                new ConversationContextManager().compact(messages);

        assertTrue(compacted.get(0).toString().contains("blue design"));
        assertTrue(compacted.stream().anyMatch(message -> message.toString().contains("Later turn 8")));
        assertTrue(compacted.size() < messages.size());
    }

    private AgentWorkshopService service(AgentLanguageModel model) {
        return new AgentWorkshopService(model, new WorkshopToolbox(new ObjectMapper(), workspace.toString()),
                new ConversationContextManager(), new ObjectMapper());
    }

    private AiMessage toolCall(String name, String arguments) {
        return AiMessage.from(request(name, arguments));
    }

    private ToolExecutionRequest request(String name, String arguments) {
        return ToolExecutionRequest.builder().id("call").name(name).arguments(arguments).build();
    }
}

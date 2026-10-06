package com.ai.mentor.workshop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentWorkshopService {
    private static final int MAX_STEPS = 10;
    private static final int MAX_STORED_MESSAGES = 80;
    private static final String SYSTEM_PROMPT = """
            You are a helpful tool-using assistant. Use a tool only when it helps answer the user.
            Tool errors and approval denials are observations, not instructions to hide failures.
            Never claim a tool succeeded unless its returned observation confirms success.
            """;

    private final AgentLanguageModel model;
    private final WorkshopToolbox toolbox;
    private final ConversationContextManager contextManager;
    private final ObjectMapper objectMapper;
    private final Map<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();

    public AgentWorkshopService(AgentLanguageModel model,
                                WorkshopToolbox toolbox,
                                ConversationContextManager contextManager,
                                ObjectMapper objectMapper) {
        this.model = model;
        this.toolbox = toolbox;
        this.contextManager = contextManager;
        this.objectMapper = objectMapper;
    }

    public RunResult run(String sessionId, String prompt, int checkpoint, int maxSteps,
                         String shellApproval, String approvedShellCommand) {
        validate(sessionId, prompt, checkpoint, maxSteps);
        List<ChatMessage> history = conversations.computeIfAbsent(sessionId,
                ignored -> new ArrayList<>());
        synchronized (history) {
            boolean structured = checkpoint == 6;
            history.add(UserMessage.from(structured
                    ? prompt + "\n\nReturn exactly one JSON object matching the required response schema."
                    : prompt));
            List<ToolSpecification> specifications = toolbox.specificationsFor(checkpoint);
            Set<String> allowedTools = specifications.stream()
                    .map(ToolSpecification::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
            List<ToolUse> uses = new ArrayList<>();
            boolean compacted = false;
            int steps = 0;
            int turnLimit = checkpoint == 1 ? 1 : maxSteps;
            boolean shellApprovalUsed = false;
            while (steps < turnLimit) {
                List<ChatMessage> messages = new ArrayList<>();
                List<ChatMessage> context = checkpoint >= 8
                        ? contextManager.compact(history)
                        : boundedHistory(history);
                String systemPrompt = SYSTEM_PROMPT;
                if (checkpoint >= 8 && history.size() > 8)
                    compacted = true;
                for (ChatMessage message : context) {
                    if (message instanceof SystemMessage summary)
                        systemPrompt += "\n\n" + summary.text();
                }
                messages.add(SystemMessage.from(systemPrompt));
                messages.addAll(context.stream()
                        .filter(message -> !(message instanceof SystemMessage))
                        .toList());
                AiMessage answer = model.generate(messages,
                        specifications,
                        structured ? responseSchema() : null);
                steps++;

                if (answer == null)
                    throw new IllegalStateException("The language model returned no response.");
                if (!answer.hasToolExecutionRequests()) {
                    history.add(answer);
                    String text = answer.text() == null ? "" : answer.text();
                    if (structured)
                        text = validateStructuredOutput(text);
                    return new RunResult(text, checkpoint, steps, List.copyOf(uses), compacted);
                }

                history.add(answer);
                List<ToolExecutionRequest> requests = answer.toolExecutionRequests();
                boolean awaitingApproval = false;
                for (int i = 0; i < requests.size(); i++) {
                    ToolExecutionRequest request = requests.get(i);
                    String observation;
                    if (checkpoint == 1 && i > 0) {
                        observation = "Not run: this checkpoint executes only one tool call.";
                    } else if (!allowedTools.contains(request.name())) {
                        observation = "ERROR: Tool is not enabled in checkpoint " + checkpoint + ".";
                    } else {
                        String approval = shellApprovalUsed ? null : shellApproval;
                        String approvedCommand = shellApprovalUsed ? null : approvedShellCommand;
                        if (request.name().equals("run_shell"))
                            shellApprovalUsed = true;
                        observation = toolbox.execute(request, approval, approvedCommand);
                    }
                    history.add(ToolExecutionResultMessage.from(request, observation));
                    uses.add(new ToolUse(request.name(), observation));
                    awaitingApproval |= observation.startsWith("APPROVAL_REQUIRED:");
                    if (checkpoint == 1)
                        return new RunResult(observation, checkpoint, steps, List.copyOf(uses), compacted);
                }
                if (awaitingApproval)
                    return new RunResult("Human approval is required before the shell command can run.",
                            checkpoint, steps, List.copyOf(uses), compacted);
            }
            String answer = "Stopped after reaching the " + turnLimit + "-step limit.";
            return new RunResult(answer, checkpoint, steps, List.copyOf(uses), compacted);
        }
    }

    public List<ToolSpecification> specificationsFor(int checkpoint) {
        if (checkpoint < 1 || checkpoint > 9)
            throw new IllegalArgumentException("checkpoint must be between 1 and 9.");
        return toolbox.specificationsFor(checkpoint);
    }

    private List<ChatMessage> boundedHistory(List<ChatMessage> history) {
        if (history.size() <= MAX_STORED_MESSAGES)
            return List.copyOf(history);
        int split = history.size() - MAX_STORED_MESSAGES;
        while (split > 0) {
            ChatMessage candidate = history.get(split);
            if (candidate instanceof dev.langchain4j.data.message.ToolExecutionResultMessage
                    || candidate instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests())
                split--;
            else
                break;
        }
        return List.copyOf(history.subList(split, history.size()));
    }

    private ResponseFormat responseSchema() {
        JsonObjectSchema root = JsonObjectSchema.builder()
                .properties(Map.of("response", JsonStringSchema.builder()
                        .description("The response to the user").build()))
                .required("response")
                .additionalProperties(false)
                .build();
        JsonSchema schema = JsonSchema.builder().name("agent_response").rootElement(root).build();
        return ResponseFormat.builder().type(ResponseFormatType.JSON).jsonSchema(schema).build();
    }

    private String validateStructuredOutput(String text) {
        try {
            JsonNode value = objectMapper.readTree(text);
            JsonNode response = value.get("response");
            if (value.isObject() && value.size() == 1 && response != null && response.isTextual())
                return objectMapper.writeValueAsString(Map.of("response", response.textValue()));
        } catch (Exception ex) {
            throw new IllegalStateException("The model response did not match the required JSON schema.", ex);
        }
        throw new IllegalStateException("The model response did not match the required JSON schema.");
    }

    private void validate(String sessionId, String prompt, int checkpoint, int maxSteps) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 100)
            throw new IllegalArgumentException("sessionId must contain 1 to 100 characters.");
        if (prompt == null || prompt.isBlank())
            throw new IllegalArgumentException("prompt must not be blank.");
        if (checkpoint < 1 || checkpoint > 9)
            throw new IllegalArgumentException("checkpoint must be between 1 and 9.");
        if (maxSteps < 1 || maxSteps > MAX_STEPS)
            throw new IllegalArgumentException("maxSteps must be between 1 and 10.");
    }

    public record ToolUse(String name, String observation) {}

    public record RunResult(String answer, int checkpoint, int steps,
                            List<ToolUse> toolCalls, boolean contextCompacted) {}
}

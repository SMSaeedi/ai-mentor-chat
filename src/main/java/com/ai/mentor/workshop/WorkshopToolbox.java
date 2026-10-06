package com.ai.mentor.workshop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class WorkshopToolbox {
    private static final int MAX_TOOL_OUTPUT = 10_000;
    private final ObjectMapper objectMapper;
    private final Path workspace;
    private final HttpClient httpClient;

    public WorkshopToolbox(ObjectMapper objectMapper,
                           @Value("${agent.workspace:${user.dir}/agent-workspace}") String workspace) {
        this.objectMapper = objectMapper;
        this.workspace = Path.of(workspace).toAbsolutePath().normalize();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public List<ToolSpecification> specificationsFor(int checkpoint) {
        List<ToolSpecification> specifications = new ArrayList<>();
        specifications.add(spec("get_time", "Get the current UTC date and time."));
        if (checkpoint >= 5) {
            specifications.add(spec("read_file", "Read a UTF-8 file inside the agent workspace.",
                    "path", JsonSchemaProperty.STRING));
            specifications.add(spec("write_file", "Write UTF-8 content to a file inside the agent workspace.",
                    "path", JsonSchemaProperty.STRING, "content", JsonSchemaProperty.STRING));
            specifications.add(spec("edit_file", "Replace one exact text fragment in a workspace file.",
                    "path", JsonSchemaProperty.STRING, "find", JsonSchemaProperty.STRING,
                    "replace", JsonSchemaProperty.STRING));
        }
        if (checkpoint >= 7)
            specifications.add(spec("web_search", "Search the public web for up-to-date information.",
                    "query", JsonSchemaProperty.STRING));
        if (checkpoint >= 9)
            specifications.add(spec("run_shell", "Run a shell command in the workspace. Requires explicit human y/n approval.",
                    "command", JsonSchemaProperty.STRING));
        return List.copyOf(specifications);
    }

    public String execute(ToolExecutionRequest request, String approval, String approvedCommand) {
        try {
            JsonNode arguments = objectMapper.readTree(request.arguments());
            if (arguments == null || !arguments.isObject())
                return "ERROR: Tool arguments must be a JSON object.";
            return switch (request.name()) {
                case "get_time" -> Instant.now().toString();
                case "read_file" -> readFile(requiredText(arguments, "path"));
                case "write_file" -> writeFile(requiredText(arguments, "path"),
                        requiredText(arguments, "content"));
                case "edit_file" -> editFile(requiredText(arguments, "path"),
                        requiredText(arguments, "find"), requiredText(arguments, "replace"));
                case "web_search" -> webSearch(requiredText(arguments, "query"));
                case "run_shell" -> runShell(requiredText(arguments, "command"), approval, approvedCommand);
                default -> "ERROR: Unknown tool '" + request.name() + "'.";
            };
        } catch (IOException | IllegalArgumentException ex) {
            return "ERROR: " + ex.getMessage();
        }
    }

    private ToolSpecification spec(String name, String description, Object... parameters) {
        ToolSpecification.Builder builder = ToolSpecification.builder().name(name).description(description);
        for (int i = 0; i < parameters.length; i += 2)
            builder.addParameter((String) parameters[i], (JsonSchemaProperty) parameters[i + 1]);
        return builder.build();
    }

    private String requiredText(JsonNode arguments, String name) {
        JsonNode value = arguments.get(name);
        if (value == null || !value.isTextual())
            throw new IllegalArgumentException("Tool argument '" + name + "' must be a string.");
        return value.textValue();
    }

    private Path workspaceRoot() throws IOException {
        Files.createDirectories(workspace);
        return workspace.toRealPath();
    }

    private Path safePath(String relative) throws IOException {
        Path root = workspaceRoot();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root))
            throw new IllegalArgumentException("Path must stay inside the agent workspace.");
        return target;
    }

    private Path safeExistingPath(String relative) throws IOException {
        Path root = workspaceRoot();
        Path target = safePath(relative);
        Path realTarget = target.toRealPath();
        if (!realTarget.startsWith(root))
            throw new IllegalArgumentException("Path must stay inside the agent workspace.");
        return realTarget;
    }

    private String readFile(String relative) throws IOException {
        return truncate(Files.readString(safeExistingPath(relative), StandardCharsets.UTF_8));
    }

    private String writeFile(String relative, String content) throws IOException {
        Path target = safePath(relative);
        Path root = workspaceRoot();
        verifyExistingAncestors(target.getParent(), root);
        Files.createDirectories(target.getParent());
        if (!target.getParent().toRealPath().startsWith(root))
            throw new IllegalArgumentException("Path must stay inside the agent workspace.");
        if (Files.exists(target) && !safeExistingPath(relative).startsWith(root))
            throw new IllegalArgumentException("Path must stay inside the agent workspace.");
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return "Wrote " + relative + " (" + content.length() + " characters).";
    }

    private void verifyExistingAncestors(Path parent, Path root) throws IOException {
        Path current = root;
        for (Path part : root.relativize(parent)) {
            current = current.resolve(part);
            if (Files.exists(current) && !current.toRealPath().startsWith(root))
                throw new IllegalArgumentException("Path must stay inside the agent workspace.");
        }
    }

    private String editFile(String relative, String find, String replace) throws IOException {
        Path target = safeExistingPath(relative);
        String content = Files.readString(target, StandardCharsets.UTF_8);
        if (!content.contains(find))
            throw new IllegalArgumentException("The requested text was not found; file was not changed.");
        Files.writeString(target, content.replace(find, replace), StandardCharsets.UTF_8);
        return "Updated " + relative + ".";
    }

    private String webSearch(String query) throws IOException {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_html=1"))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "AI-Mentor-Agent/1.0")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2)
                return "ERROR: Web search returned HTTP " + response.statusCode() + ".";
            JsonNode root = objectMapper.readTree(response.body());
            List<String> results = new ArrayList<>();
            collectSearchResults(root.path("RelatedTopics"), results, 5);
            String abstractText = root.path("AbstractText").asText("");
            if (!abstractText.isBlank())
                results.add(0, abstractText + " " + root.path("AbstractURL").asText(""));
            return results.isEmpty() ? "No search results were returned." : String.join("\n", results);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "ERROR: Web search was interrupted.";
        }
    }

    private void collectSearchResults(JsonNode topics, List<String> results, int limit) {
        for (JsonNode topic : topics) {
            if (results.size() >= limit)
                return;
            if (topic.has("Topics")) {
                collectSearchResults(topic.path("Topics"), results, limit);
                continue;
            }
            String text = topic.path("Text").asText("");
            if (!text.isBlank())
                results.add(text + " " + topic.path("FirstURL").asText(""));
        }
    }

    private String runShell(String command, String approval, String approvedCommand) throws IOException {
        if (approval == null || approval.isBlank())
            return "APPROVAL_REQUIRED: Review this command and provide shellApproval='y' or 'n': " + command;
        if (!approval.equalsIgnoreCase("y"))
            return "DENIED: The user did not approve this shell command.";
        if (!command.equals(approvedCommand))
            return "APPROVAL_REQUIRED: Approval must include the exact reviewed command in approvedShellCommand: "
                    + command;

        Path root = workspaceRoot();
        Path output = Files.createTempFile(root, "agent-shell-", ".log");
        try {
            List<String> invocation = System.getProperty("os.name").toLowerCase().contains("win")
                    ? List.of("cmd.exe", "/c", command)
                    : List.of("sh", "-lc", command);
            Process process = new ProcessBuilder(invocation)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            boolean finished;
            try {
                finished = process.waitFor(15, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                return "ERROR: Shell execution was interrupted.";
            }
            if (!finished) {
                process.destroyForcibly();
                return "ERROR: Shell command exceeded the 15-second time limit.";
            }
            String result = Files.readString(output, StandardCharsets.UTF_8);
            return "Exit code " + process.exitValue() + ":\n" + truncate(result);
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private String truncate(String text) {
        return text.length() > MAX_TOOL_OUTPUT
                ? text.substring(0, MAX_TOOL_OUTPUT) + "\n[output truncated]"
                : text;
    }
}

# Local Ollama Chat API Guide

This project uses a local Ollama model for chat, the English teacher, and Spring AI evaluations.

## 1) Configure the model provider

Install and start [Ollama](https://ollama.com/download), then download the default model:

```powershell
ollama pull qwen3.5:4b
```

The default settings in `src/main/resources/application.properties` are:

```properties
ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}
ollama.model=${OLLAMA_MODEL:qwen3.5:4b}
ollama.timeout-seconds=${OLLAMA_TIMEOUT_SECONDS:180}
```

All model-backed endpoints share this Ollama model and timeout. Keep
`OLLAMA_TIMEOUT_SECONDS` at `180` or higher for local generation; lower values can cause
gateway timeouts while Ollama loads or generates a response. In IntelliJ, remove an older
`OLLAMA_TIMEOUT_SECONDS=15` override or change it to `180`, then stop and restart the app.

## AI model failover

All model-backed features use local Ollama first and automatically retry a failed request
through an OpenAI-compatible fallback. Configure the fallback in the IntelliJ run configuration
or Windows user environment variables:

```text
AI_FALLBACK_API_KEY=your-provider-api-key
AI_FALLBACK_MODEL=gpt-4o-mini
AI_FALLBACK_BASE_URL=https://api.openai.com/v1
```

Use another provider's OpenAI-compatible chat-completions URL and model name if preferred.
The fallback is disabled until `AI_FALLBACK_API_KEY` is set; do not commit API keys to the
repository. `AI_FALLBACK_ENABLED=false` disables failover. `OLLAMA_MAX_ATTEMPTS` defaults to
`1`, so a failed local call moves to the fallback without waiting through repeated Ollama retries.
After Ollama fails, the app skips it for 30 seconds (`AI_PRIMARY_RECOVERY_SECONDS`) while sending
requests to the fallback, then probes Ollama again. The first request during a new Ollama outage
can still wait up to `OLLAMA_TIMEOUT_SECONDS` before switching providers. Restart the application
after changing environment variables. If neither service is available, the request still fails and
returns an error rather than a fabricated answer.

Start the application with the local model:

```powershell
.\mvnw.cmd spring-boot:run
```

Ollama runs locally and does not require a cloud API key or consume hosted-model API quota. If a different Ollama model is installed, set `OLLAMA_MODEL` to its exact model tag before starting the app.

## 2) Run the application

```bash
./mvnw spring-boot:run
```

or

```bash
mvn spring-boot:run
```

The app runs on:
```text
http://localhost:8080
```

## H2 database

The application uses a file-backed H2 database:

```text
JDBC URL: jdbc:h2:file:C:/Users/mahsa/IdeaProjects/chat-bot/data/mentor-db;AUTO_SERVER=TRUE
User:     sa
Password: (empty)
Console:  http://localhost:8080/h2-console
```

In the H2 console, use the JDBC URL exactly as shown above. The database is initialized at application startup from `schema.sql`, `data.sql`, and the explicit `DatabaseInitializer`.

Current tables:

```sql
select * from vendors;
select * from response_cache;
```

`vendors` contains the vendor knowledge used by retrieval. `response_cache` contains generated responses saved by the CAG cache.

## 3) Chat API

### Endpoint
```http
POST /api/mentor/chat?userId=default-user
Content-Type: application/json
```

### Request body
```json
{
  "message": "Create a 5-day plan to improve my backend skills."
}
```

### Example using curl
```bash
curl -X POST "http://localhost:8080/api/mentor/chat?userId=default-user" \
  -H "Content-Type: application/json" \
  -d '{"message":"Create a 5-day plan to improve my backend skills."}'
```

### Success response
```json
{
  "response": "Here is a simple 5-day learning plan..."
}
```

The controller returns a `ChatResponse` record:
```java
public record ChatResponse(String response) {}
```

## 4) Agent pipeline

Requests now pass through a local orchestration layer:

1. `QuestionClassifier` categorizes requests as `GENERAL`, `MATH`, `HARM`, or `POLITICS`.
2. Harm and politics requests are intercepted before model generation.
3. `ResponseCache` provides a CAG-style fast path for repeated user requests.
4. `VendorKnowledgeBase` supplies relational-style vendor records.
5. `RetrievalService` supplies category-aware RAG context.
6. The `MentorAgent` generates the answer using the local Ollama model, and `ConversationMemory` keeps recent user context.
7. `PipelineMetrics` records category, cache, safety, and generation counters.

Classification and metrics are available through:
```http
GET /api/mentor/classify?question=calculate%202%2B2
GET /api/mentor/metrics
```

Vendor records and response cache entries are persisted in a file-backed H2 database. RAG uses a local vector store with deterministic hashed vectors and cosine similarity, so it requires no external embedding service. The storage interfaces can later be moved to PostgreSQL, Redis, or a managed vector database without changing the controller or agent contract.

## 5) Tool-using agent walkthrough

The separate `POST /api/agent/run` endpoint implements the nine checkpoints below without changing the mentor chat endpoint. The agent uses the local Ollama model and retains a bounded conversation per `sessionId`.

| Checkpoint | Capability |
|---|---|
| 1 | One `get_time` tool call; returns its observation directly |
| 2 | Single-turn tool-choice eval, including a prompt expected to use no tool |
| 3 | Bounded agent loop; stops when the model returns no tool call |
| 4 | Multi-turn tool-choice trajectory eval, including a no-tool turn |
| 5 | Workspace-confined `read_file`, `write_file`, and `edit_file` tools |
| 6 | JSON response schema, with downstream shape validation |
| 7 | Public web search via DuckDuckGo Instant Answer |
| 8 | Old turns compacted into a summary while recent tool decisions/results stay in context |
| 9 | Shell execution gated by a human's explicit y/n decision |

The checkpoint and available tools can be inspected with:

```http
GET /api/agent/tools?checkpoint=9
```

Run any checkpoint with:

```http
POST /api/agent/run
Content-Type: application/json

{
  "sessionId": "demo",
  "prompt": "What time is it?",
  "checkpoint": 3,
  "maxSteps": 6
}
```

`maxSteps` is limited to 1–10. The response includes the answer, model-step count, tool observations, and whether context was compacted. Evaluate tool selection at `GET /api/agent/evals/single-turn` and `GET /api/agent/evals/multi-turn`.

Filesystem operations are confined to `AGENT_WORKSPACE` (defaults to `agent-workspace` under the project directory); paths that escape it or resolve through an outside symlink are rejected. Web search sends the model-selected query to DuckDuckGo and requires an internet connection.

Shell calls first return an `APPROVAL_REQUIRED` observation that shows the exact command, without executing it. Review that command before deciding. On the follow-up request, include `shellApproval: "y"` and copy the reviewed command exactly into `approvedShellCommand`; `"n"` denies execution, and the denial is returned to the model as a normal tool observation. An approval for a different command is rejected. Shell runs inside the workspace and times out after 15 seconds.

Example approval request body (use the exact command shown in the prior response):

```json
{
  "sessionId": "demo",
  "prompt": "Run the reviewed shell command.",
  "checkpoint": 9,
  "shellApproval": "y",
  "approvedShellCommand": "echo safe"
}
```

## 6) Response handling and error cases

The app sanitizes the AI output before returning it, removing HTML tags and unwanted formatting characters.

### Common error responses

#### Model timeout
```json
{
  "timestamp": "2026-08-30T19:00:00",
  "status": 504,
  "error": "Gateway Timeout",
  "message": "The AI model did not respond in time. Please retry the request."
}
```

## 7) Quick checklist

- Run Ollama and install the configured `OLLAMA_MODEL` first
- Restart the application after changing Ollama settings
- Ensure the app is running on port `8080`
- Send POST requests to `/api/mentor/chat`
- Use JSON body with a `message` field
- Check for `504` when the model times out

## 8) Example of a complete request

```bash
curl --location --request POST 'http://localhost:8080/api/mentor/chat?userId=demo-user' \
  --header 'Content-Type: application/json' \
  --data '{
    "message": "Give me a short summary of REST APIs and explain how a client-server chat app works."
  }'
```

Example response:
```json
{
  "response": "REST APIs use HTTP methods like GET, POST, PUT, and DELETE to communicate between a client and server. In a chat app, the client sends messages to the server, the server processes them, and the model responds with generated answers."
}
```

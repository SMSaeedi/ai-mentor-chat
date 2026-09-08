# Gemini API Configuration and Chat API Guide

This project uses the Google Gemini API through LangChain4j and exposes a REST endpoint for chat requests.

## 1) Configure the Gemini API key

The application reads the key from the `GEMINI_API_KEY` environment variable.

### Linux / macOS
```bash
export GEMINI_API_KEY="your_api_key_here"
```

### Windows PowerShell
```powershell
$env:GEMINI_API_KEY="your_api_key_here"
```

### Windows CMD
```cmd
set GEMINI_API_KEY=your_api_key_here
```

The property is defined in `src/main/resources/application.properties`:
```properties
server.port=8080
gemini.api.key=${GEMINI_API_KEY:}
gemini.model=${GEMINI_MODEL:gemini-3.6-flash}
gemini.timeout-seconds=${GEMINI_TIMEOUT_SECONDS:90}
```

Notes:
- `gemini.api.key` loads the value from the environment variable.
- If the variable is missing, the app starts with an empty key and the Gemini API will reject requests.
- The model is set to `gemini-3.6-flash` by default. Override it with `GEMINI_MODEL` when using another model available to your API account.

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
2. Harm and politics requests are intercepted before Gemini.
3. `ResponseCache` provides a CAG-style fast path for repeated user requests.
4. `VendorKnowledgeBase` supplies relational-style vendor records.
5. `RetrievalService` supplies category-aware RAG context.
6. The existing Gemini `MentorAgent` generates the answer, and `ConversationMemory` keeps recent user context.
7. `PipelineMetrics` records category, cache, safety, and generation counters.

Classification and metrics are available through:
```http
GET /api/mentor/classify?question=calculate%202%2B2
GET /api/mentor/metrics
```

Vendor records and response cache entries are persisted in a file-backed H2 database. RAG uses a local vector store with deterministic hashed vectors and cosine similarity, so it requires no external embedding service. The storage interfaces can later be moved to PostgreSQL, Redis, or a managed vector database without changing the controller or agent contract.

## 5) Response handling and error cases

The app sanitizes the AI output before returning it, removing HTML tags and unwanted formatting characters.

### Common error responses

#### Invalid API key
```json
{
  "timestamp": "2026-08-30T19:00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid Gemini API Key provided. Please verify your configuration."
}
```

#### Gemini timeout
```json
{
  "timestamp": "2026-08-30T19:00:00",
  "status": 504,
  "error": "Gateway Timeout",
  "message": "The Gemini service did not respond in time. Please retry the request."
}
```

## 6) Configuration details

The Gemini client is configured in `src/main/java/com/ai/mentor/config/MentorConfig.java`:

```java
GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
    .apiKey(apiKey)
    .modelName(modelName)
    .temperature(0.3)
    .timeout(Duration.ofSeconds(90))
    .build();
```

This means:
- API key is injected from `gemini.api.key`
- Model defaults to `gemini-3.6-flash`
- Temperature is set to `0.3` for more deterministic responses
- Request timeout is `90 seconds`

## 7) Quick checklist

- Set `GEMINI_API_KEY` before starting the app
- Remove any old `GEMINI_MODEL` value such as `gemini-2.0-flash`, or set it explicitly to `gemini-3.6-flash`
- Restart the application after changing `GEMINI_API_KEY` or `GEMINI_MODEL`
- Ensure the app is running on port `8080`
- Send POST requests to `/api/mentor/chat`
- Use JSON body with a `message` field
- Check for `401` when the key is invalid and `504` when the Gemini service times out

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

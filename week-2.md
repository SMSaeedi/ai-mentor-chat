# Week 2: Spring AI Evaluation and an English Teacher

This week adds a dedicated English-teaching endpoint and a Java evaluation suite powered by Spring AI. No PDF was present in the repository, so the implementation covers the two evaluation tasks specified in the request and extends the existing tool-selection eval cases.

## English teacher

`POST /api/english-teacher/lesson` accepts a learner question and an optional proficiency level. The teacher responds in English, adapts explanations to the level, corrects learner-provided English constructively, and uses examples or short practice activities where appropriate. The default level is `intermediate`.

```json
{
  "question": "Is this sentence correct: 'She go to work every day'?",
  "proficiencyLevel": "A2"
}
```

Successful response:

```json
{
  "answer": "The correct sentence is: 'She goes to work every day.' ..."
}
```

The question is required and limited to 2,000 characters; the optional proficiency level is limited to 50 characters.

## Spring AI evaluation suites

The evaluation endpoint runs the application cases directly and uses Spring AI with the configured local Ollama model as the evaluator:

- **Agent tools** — two cases check that the agent calls `get_time` when asked for the current time and does not call tools for a joke request.
- **English teacher** — three cases cover grammar correction, `borrow` versus `lend`, and guided role-play practice. The judge scores answer relevance, English accuracy, teaching quality, and bias/safety.

Every metric is scored from 0 to 1. Each case passes only when all of its metric scores meet the 0.7 threshold. The aggregate is the average case score. This is a Spring AI judge implementation; it does not use the DeepEval Python library or its built-in metrics. Judge scores can vary.

## How to run and test

1. Start Ollama, download the configured model, then run the application:

   ```powershell
   ollama pull qwen3.5:4b
   .\mvnw.cmd spring-boot:run
   ```

   Ollama must be running at `http://localhost:11434`. `OLLAMA_MODEL` can select a different model tag.

2. In another terminal, run the complete eval suite:

   ```powershell
   curl.exe -X POST "http://localhost:8080/api/agent/evals/spring-ai?suite=all"
   ```

   Choose a suite:

   ```powershell
   curl.exe -X POST "http://localhost:8080/api/agent/evals/spring-ai?suite=agent"
   curl.exe -X POST "http://localhost:8080/api/agent/evals/spring-ai?suite=teacher"
   ```

   The response includes per-case outputs, metric scores and reasons, pass/fail values, and the aggregate score.

   No Python installation or `.py` runner is needed.

3. Smoke-test the teacher endpoint:

   ```powershell
   Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/english-teacher/lesson `
     -ContentType "application/json" `
     -Body '{"question":"What is the difference between borrow and lend?","proficiencyLevel":"B1"}'
   ```

4. Run the Java test suite:

   ```powershell
   .\mvnw.cmd test
   ```

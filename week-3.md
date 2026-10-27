# Week 3: Basic Multi-LLM Workflows and Customer Support

This week adds Java versions of three basic multi-LLM workflows: prompt-chaining, parallelization, and routing. It also demonstrates routing customer-support tickets to specialist prompts. These are sample workflows, not production customer-service automation.

## Existing implementation check

Before adding these workflows, the project already had a chat orchestration pipeline with question classification, retrieval, caching, and conversation memory, along with a separate tool-using agent. It did not have these prompt-chaining, parallel-input, or specialist-prompt-routing workflows.

The new workflow service uses the existing configured `ChatLanguageModel`. Model provider configuration and the existing local-first/fallback behavior are unchanged.

## Prompt-chaining

`POST /api/workflows/chain` runs prompts sequentially. Each model response becomes the input to the next prompt.

```http
POST /api/workflows/chain
Content-Type: application/json

{
  "input": "Customer satisfaction is 92 points and revenue grew 45%.",
  "prompts": [
    "Extract each numeric value and its metric.",
    "Format the extracted metrics as a markdown table."
  ]
}
```

The request needs a nonblank `input` and at least one nonblank prompt.

## Parallelization

`POST /api/workflows/parallel` applies one prompt concurrently to independent inputs. Responses are returned in the same order as the inputs. The optional `workers` value defaults to `3` and must be between `1` and `16`.

```http
POST /api/workflows/parallel
Content-Type: application/json

{
  "prompt": "Summarize the stakeholder's concerns and suggest actions.",
  "inputs": [
    "Customer: price sensitive, wants better technology",
    "Employee: concerned about job security and training"
  ],
  "workers": 2
}
```

An empty inputs list returns an empty result. Invalid input and model-call failures are surfaced as errors rather than being replaced with successful-looking results.

## Prompt routing

`POST /api/workflows/route` asks the model to select a route from the provided names, then uses that route's prompt to answer the input. The request supplies route-name-to-prompt mappings:

```http
POST /api/workflows/route
Content-Type: application/json

{
  "input": "I cannot log into my account.",
  "routes": {
    "account": "You are an account support specialist. Prioritize safe account recovery.",
    "technical": "You are a technical support engineer. Provide troubleshooting steps."
  }
}
```

Route names are normalized to lowercase; duplicates ignoring case are rejected. If the model selects a route that was not supplied, the workflow fails explicitly instead of choosing an arbitrary prompt.

## Customer-support ticket example

`POST /api/support/ticket` uses built-in specialist prompts for four routes: `billing`, `technical`, `account`, and `product`.

```http
POST /api/support/ticket
Content-Type: application/json

{
  "message": "I noticed an unexpected charge on my card. Can you explain it?"
}
```

The JSON response contains `selectedRoute` and `response`. The prompts encourage safe account recovery and prohibit unsupported claims such as asserting that an account was reviewed or a refund issued.

This is a routing and response-drafting demonstration only. It does not retrieve customer or order records, issue refunds, or update tickets. Do not treat its response as confirmation that any account action occurred.

## Testing

Run the Java tests with:

```powershell
.\mvnw.cmd test
```

The tests cover sequential chaining, parallel execution and output ordering, support-ticket route selection, invalid selections, and worker-count validation.

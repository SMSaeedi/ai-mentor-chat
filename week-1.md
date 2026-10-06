# Week 1: Building Reliable Agents

## Q1. Explain exactly what an agent loop is and write one without a framework

An agent loop is the control flow that lets a language model work toward a request using tools. The application sends the conversation and available tool descriptions to the model. The model either returns a final answer or requests a tool call. The application validates and executes that call, adds the result to the conversation as an observation, and asks the model what to do next. The loop ends when the model answers without requesting a tool, or a step limit is reached.

The model proposes actions; application code decides which tools are allowed and executes them.

```text
messages = [user_message]

for step from 1 to max_steps:
    reply = model.generate(messages, tool_schemas)
    messages.append(reply)

    if reply has no tool calls:
        return reply.text

    for call in reply.tool_calls:
        if call.name is not in allowed_tools:
            result = "ERROR: tool is not available"
        else:
            result = execute_tool(call.name, call.arguments)
        messages.append(tool_result(call.id, result))

return "Stopped because the step limit was reached."
```

## Q2. Design tool schemas a model can actually use correctly

A tool schema is the model’s API contract. Use a clear, specific name; describe when to use the tool and its limits; and define arguments with precise types and required fields. Keep each tool focused, validate arguments in application code, and return understandable errors as tool results so the model can recover.

For example:

```json
{
  "name": "read_file",
  "description": "Read a UTF-8 text file inside the agent workspace. Do not use for paths outside that workspace.",
  "parameters": {
    "type": "object",
    "properties": {
      "path": {
        "type": "string",
        "description": "Workspace-relative file path, such as notes/plan.txt"
      }
    },
    "required": ["path"],
    "additionalProperties": false
  }
}
```

The schema helps the model form a valid call, but it does not replace runtime validation or authorization.

## Q3. Score an agent with automated evals instead of vibes

An eval is a repeatable test case with an input and an expected behavior. Run the agent on each case, compare what it did with the expectation, and report a score such as passed cases divided by total cases. Include positive cases where a tool should be used and negative cases where no tool should be used. For multi-turn agents, evaluate the full trajectory, including whether tool results correctly inform later turns.

For example, “What time is it?” should select `get_time`, while “Tell me a short joke” should select no tool. These checks catch regressions consistently; they complement, rather than replace, broader quality review.

## Q4. Gate irreversible actions behind human approval

Do not let a model authorize its own risky action. Have the application show the exact proposed action and pause before execution. Require an explicit human decision; on denial, return that decision to the model as an ordinary tool observation. On approval, verify that the approved action exactly matches the proposal, then apply appropriate restrictions such as a timeout and scoped working directory.

For example, a shell tool can first return `APPROVAL_REQUIRED` with the proposed command. Execute it only after the user explicitly approves that exact command. Approval should be specific to the action—not a blanket permission for future commands.

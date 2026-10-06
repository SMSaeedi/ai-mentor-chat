package com.ai.mentor.workshop;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
public class AgentWorkshopController {
    private final AgentWorkshopService agent;
    private final AgentEvaluationService evaluations;

    public AgentWorkshopController(AgentWorkshopService agent, AgentEvaluationService evaluations) {
        this.agent = agent;
        this.evaluations = evaluations;
    }

    @PostMapping("/run")
    public AgentWorkshopService.RunResult run(@RequestBody RunRequest request) {
        return agent.run(request.sessionId() == null ? "default" : request.sessionId(),
                request.prompt(), request.checkpoint() == null ? 9 : request.checkpoint(),
                request.maxSteps() == null ? 6 : request.maxSteps(),
                request.shellApproval(), request.approvedShellCommand());
    }

    @GetMapping("/tools")
    public List<String> tools(@RequestParam(defaultValue = "9") int checkpoint) {
        return agent.specificationsFor(checkpoint).stream().map(specification -> specification.name()).toList();
    }

    @GetMapping("/evals/single-turn")
    public AgentEvaluationService.EvaluationResult singleTurnEvals() {
        return evaluations.singleTurn();
    }

    @GetMapping("/evals/multi-turn")
    public AgentEvaluationService.EvaluationResult multiTurnEvals() {
        return evaluations.multiTurn();
    }

    public record RunRequest(String sessionId, String prompt, Integer checkpoint,
                             Integer maxSteps, String shellApproval, String approvedShellCommand) {}
}

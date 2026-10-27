package com.ai.mentor.workshop;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
public class BasicMultiLlmWorkflowController {
    private final BasicMultiLlmWorkflowService workflows;

    public BasicMultiLlmWorkflowController(BasicMultiLlmWorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping("/chain")
    public String chain(@RequestBody ChainRequest request) {
        return workflows.chain(request.input(), request.prompts());
    }

    @PostMapping("/parallel")
    public List<String> parallel(@RequestBody ParallelRequest request) {
        int workers = request.workers() == null
                ? BasicMultiLlmWorkflowService.defaultWorkers()
                : request.workers();
        return workflows.parallel(request.prompt(), request.inputs(), workers);
    }

    @PostMapping("/route")
    public BasicMultiLlmWorkflowService.RouteResult route(@RequestBody RouteRequest request) {
        return workflows.route(request.input(), request.routes());
    }

    public record ChainRequest(String input, List<String> prompts) {
    }

    public record ParallelRequest(String prompt, List<String> inputs, Integer workers) {
    }

    public record RouteRequest(String input, Map<String, String> routes) {
    }
}

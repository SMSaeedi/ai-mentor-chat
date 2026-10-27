package com.ai.mentor.workshop;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class CustomerSupportController {
    private final BasicMultiLlmWorkflowService workflows;

    public CustomerSupportController(BasicMultiLlmWorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping("/ticket")
    public BasicMultiLlmWorkflowService.RouteResult routeTicket(@RequestBody SupportTicketRequest request) {
        return workflows.routeSupportTicket(request.message());
    }

    public record SupportTicketRequest(String message) {
    }
}

package com.codehealth.agent.web;

import com.codehealth.agent.agent.AgentOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    @Operation(summary = "Run one agent cycle now: monitor, plan, generate, validate, route")
    public AgentOrchestrator.CycleReport run() {
        return orchestrator.runCycle();
    }

    @GetMapping("/status")
    @Operation(summary = "Agent configuration and whether a cycle is running")
    public Map<String, Object> status() {
        return orchestrator.describe();
    }
}

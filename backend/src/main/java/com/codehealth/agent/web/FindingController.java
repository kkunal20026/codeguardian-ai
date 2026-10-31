package com.codehealth.agent.web;

import com.codehealth.agent.domain.FindingRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/findings")
@Transactional(readOnly = true)
public class FindingController {

    private final FindingRepository findings;

    public FindingController(FindingRepository findings) {
        this.findings = findings;
    }

    @GetMapping
    @Operation(summary = "Findings ordered by priority")
    public List<Views.FindingView> list() {
        return findings.findAllByOrderByPriorityScoreDesc().stream().map(Views.FindingView::of).toList();
    }

    @GetMapping("/{id}")
    public Views.FindingView get(@PathVariable Long id) {
        return findings.findById(id).map(Views.FindingView::of).orElseThrow(() -> new NotFoundException("Finding", id));
    }
}

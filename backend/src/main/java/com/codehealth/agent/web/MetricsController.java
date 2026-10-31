package com.codehealth.agent.web;

import com.codehealth.agent.metrics.MetricsReport;
import com.codehealth.agent.metrics.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final MetricsService metrics;

    public MetricsController(MetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping
    public MetricsReport current() {
        return metrics.refresh();
    }
}

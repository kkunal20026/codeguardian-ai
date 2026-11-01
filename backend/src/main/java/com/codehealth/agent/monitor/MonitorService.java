package com.codehealth.agent.monitor;

import com.codehealth.agent.domain.CiRunRepository;
import com.codehealth.agent.domain.CiRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: MonitorService.java
 *  Date: October 31, 2026
 * Monitor step of the agent loop: refresh every finding source.
 */
@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final CiRunRepository ciRunRepository;
    private final FlakyTestDetector flakyDetector;
    private final SonarQubeCollector sonarQubeCollector;

    public MonitorService(CiRunRepository ciRunRepository, FlakyTestDetector flakyDetector, SonarQubeCollector sonarQubeCollector) {
        this.ciRunRepository = ciRunRepository;
        this.flakyDetector = flakyDetector;
        this.sonarQubeCollector = sonarQubeCollector;
    }

    public int refresh() {
        Set<String> projects = new LinkedHashSet<>();
        ciRunRepository.findAll().stream().map(CiRun::getProject).forEach(projects::add);
        int count = 0;
        for (String project : projects) {
            count += flakyDetector.detect(project).size();
        }
        if (sonarQubeCollector.isEnabled()) {
            try {
                count += sonarQubeCollector.collect().size();
            } catch (RuntimeException runtimeException) {
                log.warn("SonarQube collection failed after retries: {}", runtimeException.getMessage());
            }
        }
        log.info("Monitor refreshed {} findings across {} projects", count, projects.size());
        return count;
    }
}
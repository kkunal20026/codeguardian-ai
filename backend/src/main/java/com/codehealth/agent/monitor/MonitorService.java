package com.codehealth.agent.monitor;

import com.codehealth.agent.domain.CiRunRepository;
import com.codehealth.agent.domain.CiRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Monitor step of the agent loop: refresh every finding source.
 */
@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final CiRunRepository runs;
    private final FlakyTestDetector flakyDetector;
    private final SonarQubeCollector sonar;

    public MonitorService(CiRunRepository runs, FlakyTestDetector flakyDetector, SonarQubeCollector sonar) {
        this.runs = runs;
        this.flakyDetector = flakyDetector;
        this.sonar = sonar;
    }

    public int refresh() {
        Set<String> projects = new LinkedHashSet<>();
        runs.findAll().stream().map(CiRun::getProject).forEach(projects::add);
        int count = 0;
        for (String project : projects) {
            count += flakyDetector.detect(project).size();
        }
        if (sonar.isEnabled()) {
            try {
                count += sonar.collect().size();
            } catch (RuntimeException ex) {
                log.warn("SonarQube collection failed after retries: {}", ex.getMessage());
            }
        }
        log.info("Monitor refreshed {} findings across {} projects", count, projects.size());
        return count;
    }
}

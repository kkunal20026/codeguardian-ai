package com.codehealth.agent.monitor;

import com.codehealth.agent.audit.AuditTrail;
import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.config.IntegrationHttp;
import com.codehealth.agent.domain.Enums;
import com.codehealth.agent.domain.Finding;
import com.codehealth.agent.domain.FindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import com.codehealth.agent.config.Retry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pulls open issues from the SonarQube Web API ({@code /api/issues/search}). Disabled unless
 * {@code codehealth.integrations.sonar.enabled=true}.
 */
@Component
public class SonarQubeCollector {

    private static final Logger log = LoggerFactory.getLogger(SonarQubeCollector.class);

    private final CodeHealthProperties.Sonar sonar;
    private final RestClient client;
    private final FindingRepository findings;
    private final AuditTrail audit;

    public SonarQubeCollector(CodeHealthProperties properties, RestClient.Builder builder,
                              FindingRepository findings, AuditTrail audit) {
        this.sonar = properties.integrations().sonar();
        this.client = IntegrationHttp.from(builder)
                .baseUrl(sonar.baseUrl() == null ? "http://localhost:9000" : sonar.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + sonar.token())
                .build();
        this.findings = findings;
        this.audit = audit;
    }

    public boolean isEnabled() {
        return sonar.enabled();
    }

    @Transactional
    public List<Finding> collect() {
        if (!sonar.enabled()) {
            return List.of();
        }
        SonarIssues response = Retry.call("sonar.issues.search", this::fetchIssues).value();
        List<Finding> touched = new ArrayList<>();
        if (response == null || response.issues() == null) {
            return touched;
        }
        for (SonarIssue issue : response.issues()) {
            String fingerprint = "sonar:" + issue.key();
            Finding finding = findings.findByFingerprint(fingerprint).orElseGet(() -> {
                Finding saved = findings.save(new Finding(fingerprint, Enums.FindingSource.SONAR,
                        Enums.FindingType.SONAR_ISSUE, sonar.projectKey(), null, filePath(issue.component()),
                        issue.message(), "Rule " + issue.rule() + " at line " + issue.line(),
                        severity(issue.severity())));
                audit.append("FINDING_INGESTED", "FINDING", saved.getId(),
                        Map.of("fingerprint", fingerprint, "type", "SONAR_ISSUE", "rule", issue.rule()));
                return saved;
            });
            touched.add(finding);
        }
        log.info("Collected {} open Sonar issues for {}", touched.size(), sonar.projectKey());
        return touched;
    }

    private SonarIssues fetchIssues() {
        return client.get()
                .uri(uri -> uri.path("/api/issues/search")
                        .queryParam("componentKeys", sonar.projectKey())
                        .queryParam("resolved", "false")
                        .queryParam("ps", 100)
                        .build())
                .retrieve()
                .body(SonarIssues.class);
    }

    private static String filePath(String component) {
        int colon = component == null ? -1 : component.indexOf(':');
        return colon < 0 ? component : component.substring(colon + 1);
    }

    private static Enums.Severity severity(String sonarSeverity) {
        try {
            return Enums.Severity.valueOf(sonarSeverity);
        } catch (RuntimeException ex) {
            return Enums.Severity.MAJOR;
        }
    }

    record SonarIssues(List<SonarIssue> issues) {
    }

    record SonarIssue(String key, String rule, String severity, String component, Integer line, String message) {
    }
}

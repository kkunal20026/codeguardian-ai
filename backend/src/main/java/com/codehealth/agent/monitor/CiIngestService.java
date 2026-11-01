package com.codehealth.agent.monitor;

import com.codehealth.agent.audit.AuditTrail;
import com.codehealth.agent.domain.CiRun;
import com.codehealth.agent.domain.CiRunRepository;
import com.codehealth.agent.domain.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: CiIngestService.java
 *  Date: October 31, 2026
 */
@Service
public class CiIngestService {

    private static final Logger log = LoggerFactory.getLogger(CiIngestService.class);

    private final CiRunRepository ciRunRepository;
    private final JUnitXmlParser jUnitXmlParser;
    private final FlakyTestDetector flakyDetector;
    private final AuditTrail auditTrail;

    public CiIngestService(CiRunRepository ciRunRepository, JUnitXmlParser jUnitXmlParser, FlakyTestDetector flakyDetector,
                           AuditTrail auditTrail) {
        this.ciRunRepository = ciRunRepository;
        this.jUnitXmlParser = jUnitXmlParser;
        this.flakyDetector = flakyDetector;
        this.auditTrail = auditTrail;
    }

    public record Summary(Long id, String runId, String conclusion, int tests, boolean duplicate) {
    }

    /**
     * Idempotent: posting the same run twice (CI retries the upload) returns the stored run unchanged.
     */
    @Transactional
    public Summary ingest(CiRunReport ciRunReport, boolean synthetic) {
        Optional<CiRun> existing = ciRunRepository.findByProjectAndRunId(ciRunReport.project(), ciRunReport.runId());
        if (existing.isPresent()) {
            log.info("CI run {} for {} already ingested, skipping", ciRunReport.runId(), ciRunReport.project());
            CiRun run = existing.get();
            return new Summary(run.getId(), run.getRunId(), run.getConclusion(), run.getResults().size(), true);
        }

        CiRun run = new CiRun(ciRunReport.runId(), ciRunReport.project(), ciRunReport.commitSha(), ciRunReport.branch(), synthetic,
                ciRunReport.startedAt() != null ? ciRunReport.startedAt() : Instant.now());
        ciRunReport.junitXml().stream()
                .flatMap(xml -> jUnitXmlParser.parse(xml).stream())
                .forEach(t -> run.addResult(new TestResult(t.testClass(), t.testName(), t.outcome(),
                        t.durationMs(), t.failureMessage())));
        CiRun saved = ciRunRepository.save(run);

        auditTrail.append("CI_RUN_INGESTED", "CI_RUN", saved.getId(), Map.of(
                "project", saved.getProject(),
                "runId", saved.getRunId(),
                "commitSha", saved.getCommitSha(),
                "conclusion", saved.getConclusion(),
                "tests", saved.getResults().size(),
                "synthetic", synthetic));
        log.info("Ingested CI run {} for {} with {} tests, conclusion {}", saved.getRunId(), saved.getProject(),
                saved.getResults().size(), saved.getConclusion());

        flakyDetector.detect(saved.getProject());
        return new Summary(saved.getId(), saved.getRunId(), saved.getConclusion(), saved.getResults().size(), false);
    }
}

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

@Service
public class CiIngestService {

    private static final Logger log = LoggerFactory.getLogger(CiIngestService.class);

    private final CiRunRepository runs;
    private final JUnitXmlParser parser;
    private final FlakyTestDetector flakyDetector;
    private final AuditTrail audit;

    public CiIngestService(CiRunRepository runs, JUnitXmlParser parser, FlakyTestDetector flakyDetector,
                           AuditTrail audit) {
        this.runs = runs;
        this.parser = parser;
        this.flakyDetector = flakyDetector;
        this.audit = audit;
    }

    public record Summary(Long id, String runId, String conclusion, int tests, boolean duplicate) {
    }

    /**
     * Idempotent: posting the same run twice (CI retries the upload) returns the stored run unchanged.
     */
    @Transactional
    public Summary ingest(CiRunReport report, boolean synthetic) {
        Optional<CiRun> existing = runs.findByProjectAndRunId(report.project(), report.runId());
        if (existing.isPresent()) {
            log.info("CI run {} for {} already ingested, skipping", report.runId(), report.project());
            CiRun run = existing.get();
            return new Summary(run.getId(), run.getRunId(), run.getConclusion(), run.getResults().size(), true);
        }

        CiRun run = new CiRun(report.runId(), report.project(), report.commitSha(), report.branch(), synthetic,
                report.startedAt() != null ? report.startedAt() : Instant.now());
        report.junitXml().stream()
                .flatMap(xml -> parser.parse(xml).stream())
                .forEach(t -> run.addResult(new TestResult(t.testClass(), t.testName(), t.outcome(),
                        t.durationMs(), t.failureMessage())));
        CiRun saved = runs.save(run);

        audit.append("CI_RUN_INGESTED", "CI_RUN", saved.getId(), Map.of(
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

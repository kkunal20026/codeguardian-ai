package com.codehealth.agent.agent;

import com.codehealth.agent.approval.ApprovalService;
import com.codehealth.agent.audit.AuditTrail;
import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.Enums.ExecutionStatus;
import com.codehealth.agent.domain.Enums.FindingStatus;
import com.codehealth.agent.domain.Enums.FindingType;
import com.codehealth.agent.domain.Enums.ProposalStatus;
import com.codehealth.agent.domain.Enums.RiskLevel;
import com.codehealth.agent.domain.Enums.ValidationStatus;
import com.codehealth.agent.domain.Execution;
import com.codehealth.agent.domain.Finding;
import com.codehealth.agent.domain.FindingRepository;
import com.codehealth.agent.domain.Proposal;
import com.codehealth.agent.domain.ProposalRepository;
import com.codehealth.agent.execution.ExecutionService;
import com.codehealth.agent.generator.GeneratedFix;
import com.codehealth.agent.generator.GenerationInput;
import com.codehealth.agent.generator.GenerationService;
import com.codehealth.agent.metrics.CodeHealthMetrics;
import com.codehealth.agent.metrics.MetricsService;
import com.codehealth.agent.monitor.MonitorService;
import com.codehealth.agent.planner.PlannerService;
import com.codehealth.agent.policy.RiskPolicy;
import com.codehealth.agent.retrieval.KnowledgeRetriever;
import com.codehealth.agent.validation.PatchValidator;
import com.codehealth.agent.validation.SourceRepository;
import com.codehealth.agent.validation.UnifiedDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The agent loop: monitor -> plan -> retrieve -> generate -> validate -> risk policy -> route
 * (auto PR / human approval / ticket) -> audit. Each finding is processed independently, so one failure
 * never blocks the rest of the cycle.
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final MonitorService monitor;
    private final PlannerService planner;
    private final KnowledgeRetriever retriever;
    private final SourceRepository source;
    private final GenerationService generator;
    private final PatchValidator validator;
    private final RiskPolicy policy;
    private final ApprovalService approvals;
    private final ExecutionService executor;
    private final FindingRepository findings;
    private final ProposalRepository proposals;
    private final AuditTrail audit;
    private final CodeHealthMetrics metrics;
    private final MetricsService metricsService;
    private final TransactionTemplate tx;
    private final CodeHealthProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AgentOrchestrator(MonitorService monitor, PlannerService planner, KnowledgeRetriever retriever,
                             SourceRepository source, GenerationService generator, PatchValidator validator,
                             RiskPolicy policy, ApprovalService approvals, ExecutionService executor,
                             FindingRepository findings, ProposalRepository proposals, AuditTrail audit,
                             CodeHealthMetrics metrics, MetricsService metricsService, TransactionTemplate tx,
                             CodeHealthProperties properties) {
        this.monitor = monitor;
        this.planner = planner;
        this.retriever = retriever;
        this.source = source;
        this.generator = generator;
        this.validator = validator;
        this.policy = policy;
        this.approvals = approvals;
        this.executor = executor;
        this.findings = findings;
        this.proposals = proposals;
        this.audit = audit;
        this.metrics = metrics;
        this.metricsService = metricsService;
        this.tx = tx;
        this.properties = properties;
    }

    public record ProposalOutcome(Long proposalId, Long findingId, String title, RiskLevel riskLevel,
                                  ProposalStatus status, String generatedBy) {
    }

    public record CycleReport(Instant startedAt, long durationMs, int findingsRefreshed, int findingsPlanned,
                              List<ProposalOutcome> outcomes, List<String> errors) {
    }

    @Scheduled(cron = "${codehealth.agent.cron}")
    public void scheduledCycle() {
        if (properties.agent().scheduleEnabled()) {
            runCycle();
        }
    }

    public CycleReport runCycle() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("An agent cycle is already running");
        }
        Instant started = Instant.now();
        try {
            tx.executeWithoutResult(s -> audit.append("AGENT_CYCLE_STARTED", "AGENT", "cycle", "agent", Map.of()));
            int refreshed = monitor.refresh();
            List<Finding> planned = planner.plan(properties.agent().maxFindingsPerCycle());

            List<ProposalOutcome> outcomes = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (Finding finding : planned) {
                try {
                    outcomes.add(process(finding));
                } catch (RuntimeException ex) {
                    log.error("Processing finding {} failed", finding.getId(), ex);
                    errors.add("Finding " + finding.getId() + ": " + ex.getMessage());
                }
            }
            metricsService.refresh();
            long durationMs = Duration.between(started, Instant.now()).toMillis();
            tx.executeWithoutResult(s -> audit.append("AGENT_CYCLE_COMPLETED", "AGENT", "cycle", "agent",
                    Map.of("planned", planned.size(), "proposals", outcomes.size(), "errors", errors.size(),
                            "durationMs", durationMs)));
            return new CycleReport(started, durationMs, refreshed, planned.size(), outcomes, errors);
        } finally {
            running.set(false);
        }
    }

    ProposalOutcome process(Finding finding) {
        // 1. Retrieve knowledge and source (RAG context)
        List<KnowledgeRetriever.RetrievedDoc> context = retriever.search(finding.getTitle() + " " + finding.getDetails(), 3);
        String sourceCode = source.read(finding.getFilePath()).orElse(null);
        GenerationInput input = new GenerationInput(finding, context, finding.getFilePath(), sourceCode);

        // 2. Generate (LLM or deterministic fallback); no DB transaction held during the model call
        GenerationService.Generated generated = generator.generate(input);
        GeneratedFix fix = generated.fix();

        // 3. Persist proposal
        Proposal proposal = tx.execute(s -> {
            Proposal created = proposals.save(new Proposal(finding.getId(), fix.prTitle(), fix.summary(),
                    fix.rootCause(), fix.explanation(), fix.patchDiff(), fix.prDescription(), fix.confidence(),
                    generated.generatedBy()));
            findings.findById(finding.getId()).ifPresent(f -> f.moveTo(FindingStatus.PROPOSED));
            audit.append("PROPOSAL_GENERATED", "PROPOSAL", created.getId(), "agent", Map.of(
                    "findingId", finding.getId(),
                    "generatedBy", generated.generatedBy(),
                    "confidence", fix.confidence(),
                    "hasPatch", fix.hasPatch(),
                    "knowledgeUsed", context.stream().map(KnowledgeRetriever.RetrievedDoc::key).toList(),
                    "retrieval", retriever.method()));
            return created;
        });
        metrics.proposalCreated();

        // 4. Validate (static or local test runs)
        PatchValidator.Result validation = fix.hasPatch()
                ? validator.validate(fix.patchDiff(), testFilter(finding))
                : new PatchValidator.Result(ValidationStatus.NOT_EXECUTED, false, 0, "No patch to validate", List.of());

        // 5. Deterministic risk policy
        List<String> touched = validation.patches().stream().map(UnifiedDiff.FilePatch::path).toList();
        int lines = validation.patches().stream().mapToInt(UnifiedDiff.FilePatch::linesChanged).sum();
        RiskPolicy.Decision decision = policy.evaluate(new RiskPolicy.Input(fix.hasPatch(), validation.applies(),
                touched, lines, fix.confidence(), validation.status(), validation.greenRuns()));

        tx.executeWithoutResult(s -> {
            Proposal p = proposals.findById(proposal.getId()).orElseThrow();
            p.recordDiffStats(lines, String.join(",", touched));
            p.recordValidation(validation.status(), validation.log());
            p.assignRisk(decision.level(), String.join("; ", decision.reasons()));
            audit.append("VALIDATION_COMPLETED", "PROPOSAL", p.getId(), "agent", Map.of(
                    "status", validation.status().name(), "applies", validation.applies(),
                    "greenRuns", validation.greenRuns()));
            audit.append("RISK_ASSESSED", "PROPOSAL", p.getId(), "agent", Map.of(
                    "riskLevel", decision.level().name(), "reasons", decision.reasons(),
                    "linesChanged", lines, "touchedFiles", touched));
        });

        // 6. Route
        Proposal routed = switch (decision.level()) {
            case LOW -> approvals.autoApprove(proposal.getId());
            case MEDIUM -> {
                Proposal pending = proposals.findById(proposal.getId()).orElseThrow();
                approvals.requestApproval(pending);
                yield pending;
            }
            case HIGH -> ticketOnly(proposal.getId(), finding);
        };
        log.info("Finding {} -> proposal {} risk {} status {}", finding.getId(), routed.getId(),
                decision.level(), routed.getStatus());
        return new ProposalOutcome(routed.getId(), finding.getId(), routed.getTitle(), decision.level(),
                routed.getStatus(), generated.generatedBy());
    }

    private Proposal ticketOnly(Long proposalId, Finding finding) {
        Proposal proposal = proposals.findById(proposalId).orElseThrow();
        Execution ticket = executor.createTicket(proposal, finding);
        return tx.execute(s -> {
            Proposal p = proposals.findById(proposalId).orElseThrow();
            p.transitionTo(ticket.getStatus() == ExecutionStatus.FAILED
                    ? ProposalStatus.FAILED : ProposalStatus.TICKET_CREATED);
            return p;
        });
    }

    /**
     * Surefire filter "SimpleClass#method" for flaky-test findings so validation re-runs only that test.
     */
    static String testFilter(Finding finding) {
        if (finding.getType() != FindingType.FLAKY_TEST) {
            return null;
        }
        String fingerprint = finding.getFingerprint();
        String test = fingerprint.substring(fingerprint.lastIndexOf(':') + 1);
        String className = test.contains("#") ? test.substring(0, test.indexOf('#')) : test;
        String method = test.contains("#") ? test.substring(test.indexOf('#') + 1) : "";
        String simple = className.substring(className.lastIndexOf('.') + 1);
        return method.isBlank() ? simple : simple + "#" + method;
    }

    public boolean isRunning() {
        return running.get();
    }

    public Map<String, Object> describe() {
        Map<String, Object> state = new HashMap<>();
        state.put("running", running.get());
        state.put("scheduleEnabled", properties.agent().scheduleEnabled());
        state.put("cron", properties.agent().cron());
        state.put("generator", properties.ai().provider());
        state.put("validation", properties.validation().mode());
        state.put("retrieval", retriever.method());
        return state;
    }
}

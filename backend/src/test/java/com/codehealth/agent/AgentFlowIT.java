package com.codehealth.agent;

import com.codehealth.agent.agent.AgentOrchestrator;
import com.codehealth.agent.approval.ApprovalService;
import com.codehealth.agent.audit.AuditTrail;
import com.codehealth.agent.domain.Enums;
import com.codehealth.agent.domain.ExecutionRepository;
import com.codehealth.agent.domain.LlmInteractionRepository;
import com.codehealth.agent.domain.Proposal;
import com.codehealth.agent.domain.ProposalRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end against a real SQL Server: synthetic CI history -> flaky detection -> plan -> template
 * generation on the real demo-repo source -> static validation -> MEDIUM risk -> human approval ->
 * dry-run PR -> audit chain verifies and rejects tampering.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "DB_PASSWORD=unused",
        "codehealth.demo.seed-synthetic-ci=true",
        "codehealth.ai.provider=template",
        "codehealth.validation.mode=static"
})
class AgentFlowIT {

    @Container
    @ServiceConnection
    static final MSSQLServerContainer SQL = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.user", SQL::getUsername);
        registry.add("spring.flyway.password", SQL::getPassword);
        registry.add("codehealth.validation.repo-path", () -> Path.of("../demo-repo").toAbsolutePath().toString());
    }

    @Autowired
    AgentOrchestrator orchestrator;
    @Autowired
    ApprovalService approvals;
    @Autowired
    ProposalRepository proposals;
    @Autowired
    ExecutionRepository executions;
    @Autowired
    LlmInteractionRepository llmCalls;
    @Autowired
    AuditTrail audit;
    @Autowired
    JdbcClient jdbc;

    @Test
    void fullAgentLoopWithHumanApprovalAndTamperEvidentAudit() {
        AgentOrchestrator.CycleReport report = orchestrator.runCycle();

        assertThat(report.errors()).isEmpty();
        assertThat(report.outcomes()).singleElement().satisfies(o -> {
            assertThat(o.riskLevel()).isEqualTo(Enums.RiskLevel.MEDIUM);
            assertThat(o.status()).isEqualTo(Enums.ProposalStatus.AWAITING_APPROVAL);
        });

        Long proposalId = report.outcomes().getFirst().proposalId();
        Proposal pending = proposals.findById(proposalId).orElseThrow();
        assertThat(pending.getPatchDiff()).contains("Thread.sleep").contains(".get(5,");
        assertThat(pending.getRiskReasons()).contains("Tests not proven green");
        assertThat(llmCalls.findAll()).isNotEmpty();

        Proposal executed = approvals.approve(proposalId, "alice", "Looks right", Enums.ApprovalChannel.UI);

        assertThat(executed.getStatus()).isEqualTo(Enums.ProposalStatus.EXECUTED);
        assertThat(executions.findByProposalIdOrderByCreatedAt(proposalId))
                .extracting(e -> e.getStatus().name())
                .contains("DRY_RUN");

        assertThat(audit.verify().valid()).isTrue();
        assertThat(audit.entriesFor("PROPOSAL", proposalId.toString()))
                .extracting(e -> e.eventType())
                .contains("PROPOSAL_GENERATED", "RISK_ASSESSED", "APPROVAL_DECIDED");

        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_chain SET actor = 'mallory'").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_chain").update())
                .hasMessageContaining("append-only");
    }
}

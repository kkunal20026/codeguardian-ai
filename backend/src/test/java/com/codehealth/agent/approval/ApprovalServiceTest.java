package com.codehealth.agent.approval;

import com.codehealth.agent.TestProperties;
import com.codehealth.agent.audit.AuditTrail;
import com.codehealth.agent.domain.ApprovalRepository;
import com.codehealth.agent.domain.Enums.ApprovalChannel;
import com.codehealth.agent.domain.Enums.ProposalStatus;
import com.codehealth.agent.domain.Enums.RiskLevel;
import com.codehealth.agent.domain.FindingRepository;
import com.codehealth.agent.domain.Proposal;
import com.codehealth.agent.domain.ProposalRepository;
import com.codehealth.agent.execution.ExecutionService;
import com.codehealth.agent.metrics.CodeHealthMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalServiceTest {

    private final ProposalRepository proposals = mock(ProposalRepository.class);
    private final ApprovalRepository approvals = mock(ApprovalRepository.class);
    private final ExecutionService executor = mock(ExecutionService.class);
    private ApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(proposals, mock(FindingRepository.class), approvals, executor, List.of(),
                mock(AuditTrail.class), mock(CodeHealthMetrics.class), new TransactionTemplate(new NoOpTx()),
                TestProperties.defaults());
    }

    @Test
    void highRiskProposalWithFailedTicketCannotBeApprovedIntoAPullRequest() {
        Proposal proposal = proposal(RiskLevel.HIGH);
        proposal.transitionTo(ProposalStatus.FAILED); // Jira call failed in ticketOnly
        when(proposals.findById(1L)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.approve(1L, "alice", "retry", ApprovalChannel.UI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HIGH risk");
        verify(executor, never()).openPullRequest(any());
        verify(approvals, never()).save(any());
    }

    @Test
    void highRiskProposalAwaitingRoutingCannotBeApproved() {
        when(proposals.findById(1L)).thenReturn(Optional.of(proposal(RiskLevel.HIGH)));

        assertThatThrownBy(() -> service.approve(1L, "alice", null, ApprovalChannel.UI))
                .isInstanceOf(IllegalStateException.class);
        verify(executor, never()).openPullRequest(any());
    }

    private static Proposal proposal(RiskLevel level) {
        Proposal proposal = new Proposal(10L, "t", "s", "r", "e", "diff", "pr", 0.9, "template");
        proposal.assignRisk(level, "reason");
        return proposal;
    }

    /**
     * Runs callbacks without a database, so the service logic can be tested on its own.
     */
    private static final class NoOpTx extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}

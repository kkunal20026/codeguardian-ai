package com.codehealth.agent.approval;

import com.codehealth.agent.domain.Proposal;

/**
 * A channel that tells reviewers a proposal is waiting. The decision itself is always made through the
 * authenticated API, so the approver identity comes from a verified token, not from a chat message.
 */
public interface ApprovalNotifier {

    String channel();

    boolean isEnabled();

    void notifyPending(Proposal proposal, String reviewUrl);
}

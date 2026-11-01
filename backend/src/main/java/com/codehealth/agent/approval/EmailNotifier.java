package com.codehealth.agent.approval;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.Proposal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: EmailNotifier.java
 *  Date: October 31, 2026
 */
@Component
public class EmailNotifier implements ApprovalNotifier {

    private final CodeHealthProperties.Email config;
    private final JavaMailSender javaMailSender;

    public EmailNotifier(CodeHealthProperties properties, ObjectProvider<JavaMailSender> javaMailSender) {
        this.config = properties.integrations().email();
        this.javaMailSender = javaMailSender.getIfAvailable();
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public boolean isEnabled() {
        return config.enabled() && javaMailSender != null && config.to() != null && !config.to().isBlank();
    }

    @Override
    public void notifyPending(Proposal proposal, String reviewUrl) {
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(config.from());
        simpleMailMessage.setTo(config.to().split("\\s*,\\s*"));
        simpleMailMessage.setSubject("[Code Health] Review needed: " + proposal.getTitle());
        simpleMailMessage.setText("""
                A code health fix is waiting for your review.

                Title: %s
                Risk: %s
                Why it needs a human: %s

                Summary:
                %s

                Review and approve: %s
                """.formatted(proposal.getTitle(), proposal.getRiskLevel(), proposal.getRiskReasons(),
                proposal.getSummary(), reviewUrl));
        javaMailSender.send(simpleMailMessage);
    }
}

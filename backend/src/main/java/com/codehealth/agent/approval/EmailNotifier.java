package com.codehealth.agent.approval;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.Proposal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotifier implements ApprovalNotifier {

    private final CodeHealthProperties.Email config;
    private final JavaMailSender mailSender;

    public EmailNotifier(CodeHealthProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        this.config = properties.integrations().email();
        this.mailSender = mailSender.getIfAvailable();
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public boolean isEnabled() {
        return config.enabled() && mailSender != null && config.to() != null && !config.to().isBlank();
    }

    @Override
    public void notifyPending(Proposal proposal, String reviewUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(config.from());
        message.setTo(config.to().split("\\s*,\\s*"));
        message.setSubject("[Code Health] Review needed: " + proposal.getTitle());
        message.setText("""
                A code health fix is waiting for your review.

                Title: %s
                Risk: %s
                Why it needs a human: %s

                Summary:
                %s

                Review and approve: %s
                """.formatted(proposal.getTitle(), proposal.getRiskLevel(), proposal.getRiskReasons(),
                proposal.getSummary(), reviewUrl));
        mailSender.send(message);
    }
}

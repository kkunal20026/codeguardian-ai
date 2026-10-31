package com.codehealth.agent.web;

import com.codehealth.agent.audit.AuditEntry;
import com.codehealth.agent.audit.AuditTrail;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditTrail audit;

    public AuditController(AuditTrail audit) {
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Audit chain entries, newest first")
    public List<AuditEntry> list(@RequestParam(defaultValue = "100") int limit,
                                 @RequestParam(required = false) Long before,
                                 @RequestParam(required = false) String entityType,
                                 @RequestParam(required = false) String entityId) {
        if (entityType != null && entityId != null) {
            return audit.entriesFor(entityType, entityId);
        }
        return audit.entries(limit, before);
    }

    @GetMapping("/verify")
    @Operation(summary = "Recompute every hash from genesis and report the first broken link, if any")
    public AuditTrail.VerificationResult verify() {
        return audit.verify();
    }
}

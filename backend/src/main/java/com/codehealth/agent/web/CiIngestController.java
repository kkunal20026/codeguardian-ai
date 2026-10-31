package com.codehealth.agent.web;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.monitor.CiIngestService;
import com.codehealth.agent.monitor.CiRunReport;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * CI pipelines post test results here after their test step. Authenticated with a shared token
 * ({@code CI_INGEST_TOKEN}) compared in constant time, because pipelines do not hold user JWTs.
 */
@RestController
@RequestMapping("/api/v1/ci")
public class CiIngestController {

    private final CiIngestService ingest;
    private final CodeHealthProperties properties;

    public CiIngestController(CiIngestService ingest, CodeHealthProperties properties) {
        this.ingest = ingest;
        this.properties = properties;
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Report one CI run with its JUnit XML results")
    public CiIngestService.Summary report(@RequestHeader(name = "X-CI-Token", required = false) String token,
                                 @RequestBody @Valid CiRunReport report) {
        checkToken(token);
        return ingest.ingest(report, false);
    }

    private void checkToken(String supplied) {
        String expected = properties.integrations().ci().ingestToken();
        if (expected == null || expected.isBlank()) {
            if (properties.security().isKeycloak()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CI_INGEST_TOKEN is not configured");
            }
            return; // demo mode without a token: accepted, documented as local-only
        }
        if (supplied == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid CI token");
        }
    }
}

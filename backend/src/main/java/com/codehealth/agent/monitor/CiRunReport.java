package com.codehealth.agent.monitor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Payload a CI pipeline posts after its test step: run metadata plus the raw JUnit XML report files.
 */
public record CiRunReport(
        @NotBlank @Size(max = 200) String project,
        @NotBlank @Size(max = 100) String runId,
        @NotBlank @Size(max = 64) String commitSha,
        @Size(max = 200) String branch,
        Instant startedAt,
        @NotEmpty List<@NotBlank String> junitXml
) {
}

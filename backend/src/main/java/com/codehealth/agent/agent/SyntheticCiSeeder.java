package com.codehealth.agent.agent;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.CiRunRepository;
import com.codehealth.agent.monitor.CiIngestService;
import com.codehealth.agent.monitor.CiRunReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: AgentOrchestrator.java
 *  Date: October 31, 2026
 * Demo profile only. Seeds 20 pre-fix CI runs for the demo order service so the dashboard has history on
 * first start. Every run is stored with synthetic = true and the dashboard says so. Post-fix numbers must come
 * from real runs (see demo-repo/scripts/run-ci-locally.ps1).
 */
@Component
public class SyntheticCiSeeder {

    private static final Logger log = LoggerFactory.getLogger(SyntheticCiSeeder.class);
    static final String PROJECT                 = "demo-order-service";
    static final String FLAKY_CLASS             = "com.demo.order.OrderPricingServiceTest";
    private static final List<String> STABLE    = List.of("calculatesBasePrice", "rejectsNegativeQuantity",
            "roundsToTwoDecimals", "appliesNoDiscountOnWeekday");

    private final CodeHealthProperties codeHealthProperties;
    private final CiRunRepository ciRunRepository;
    private final CiIngestService ciIngestService;

    public SyntheticCiSeeder(CodeHealthProperties codeHealthProperties, CiRunRepository ciRunRepository, CiIngestService ciIngestService) {
        this.codeHealthProperties = codeHealthProperties;
        this.ciRunRepository = ciRunRepository;
        this.ciIngestService = ciIngestService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!codeHealthProperties.demo().seedSyntheticCi() || ciRunRepository.count() > 0) {
            return;
        }
        Random random = new Random(42);
        Instant start = Instant.now().minus(Duration.ofDays(10));
        // Two runs per commit (a CI re-run), which is what exposes a flaky test.
        IntStream.range(0, 20).forEach(i -> {
            String commit = "%040x".formatted(0xc0de000L + i / 2);
            boolean flakyFails = random.nextDouble() < 0.4;
            ciIngestService.ingest(new CiRunReport(PROJECT, "synthetic-" + (i + 1), commit, "main",
                    start.plus(Duration.ofHours(12L * i)), List.of(junitXml(flakyFails))), true);
        });
        log.info("Seeded 20 synthetic CI runs for {}", PROJECT);
    }

    private static String junitXml(boolean flakyFails) {
        StringBuilder stringBuilderXml = new StringBuilder("<testsuite name=\"" + FLAKY_CLASS + "\">\n");
        for (String test : STABLE) {
            stringBuilderXml.append("  <testcase classname=\"").append(FLAKY_CLASS).append("\" name=\"").append(test)
                    .append("\" time=\"0.012\"/>\n");
        }
        stringBuilderXml.append("  <testcase classname=\"").append(FLAKY_CLASS)
                .append("\" name=\"appliesDiscountWhenPriceIsReady\" time=\"0.205\"");
        if (flakyFails) {
            stringBuilderXml.append(">\n    <failure message=\"price should be ready ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;\""
                    + " type=\"org.opentest4j.AssertionFailedError\"/>\n  </testcase>\n");
        } else {
            stringBuilderXml.append("/>\n");
        }
        return stringBuilderXml.append("</testsuite>\n").toString();
    }
}

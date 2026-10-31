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

/**
 * Demo profile only. Seeds 20 pre-fix CI runs for the demo order service so the dashboard has history on
 * first start. Every run is stored with synthetic = true and the dashboard says so. Post-fix numbers must come
 * from real runs (see demo-repo/scripts/run-ci-locally.ps1).
 */
@Component
public class SyntheticCiSeeder {

    private static final Logger log = LoggerFactory.getLogger(SyntheticCiSeeder.class);
    static final String PROJECT = "demo-order-service";
    static final String FLAKY_CLASS = "com.demo.order.OrderPricingServiceTest";
    private static final List<String> STABLE = List.of("calculatesBasePrice", "rejectsNegativeQuantity",
            "roundsToTwoDecimals", "appliesNoDiscountOnWeekday");

    private final CodeHealthProperties properties;
    private final CiRunRepository runs;
    private final CiIngestService ingest;

    public SyntheticCiSeeder(CodeHealthProperties properties, CiRunRepository runs, CiIngestService ingest) {
        this.properties = properties;
        this.runs = runs;
        this.ingest = ingest;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!properties.demo().seedSyntheticCi() || runs.count() > 0) {
            return;
        }
        Random random = new Random(42);
        Instant start = Instant.now().minus(Duration.ofDays(10));
        for (int i = 0; i < 20; i++) {
            // Two runs per commit (a CI re-run), which is what exposes a flaky test.
            String commit = "%040x".formatted(0xc0de000L + i / 2);
            boolean flakyFails = random.nextDouble() < 0.4;
            ingest.ingest(new CiRunReport(PROJECT, "synthetic-" + (i + 1), commit, "main",
                    start.plus(Duration.ofHours(12L * i)), List.of(junitXml(flakyFails))), true);
        }
        log.info("Seeded 20 synthetic CI runs for {}", PROJECT);
    }

    private static String junitXml(boolean flakyFails) {
        StringBuilder xml = new StringBuilder("<testsuite name=\"" + FLAKY_CLASS + "\">\n");
        for (String test : STABLE) {
            xml.append("  <testcase classname=\"").append(FLAKY_CLASS).append("\" name=\"").append(test)
                    .append("\" time=\"0.012\"/>\n");
        }
        xml.append("  <testcase classname=\"").append(FLAKY_CLASS)
                .append("\" name=\"appliesDiscountWhenPriceIsReady\" time=\"0.205\"");
        if (flakyFails) {
            xml.append(">\n    <failure message=\"price should be ready ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;\""
                    + " type=\"org.opentest4j.AssertionFailedError\"/>\n  </testcase>\n");
        } else {
            xml.append("/>\n");
        }
        return xml.append("</testsuite>\n").toString();
    }
}

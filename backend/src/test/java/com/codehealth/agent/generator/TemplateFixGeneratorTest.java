package com.codehealth.agent.generator;

import com.codehealth.agent.domain.Enums;
import com.codehealth.agent.domain.Finding;
import com.codehealth.agent.validation.UnifiedDiff;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateFixGeneratorTest {

    private final TemplateFixGenerator generator = new TemplateFixGenerator(new PromptFactory(), null);

    private static final String SOURCE = """
            class OrderPricingServiceTest {
                @Test
                void appliesDiscountWhenPriceIsReady() throws Exception {
                    CompletableFuture<BigDecimal> price = service.priceAsync(new BigDecimal("100.00"));
                    Thread.sleep(200);
                    assertTrue(price.isDone(), "price should be ready");
                }
            }
            """;

    private static Finding flaky() {
        return new Finding("flaky:p:a.OrderPricingServiceTest#appliesDiscountWhenPriceIsReady", Enums.FindingSource.CI,
                Enums.FindingType.FLAKY_TEST, "p", "pricing", "src/test/java/a/OrderPricingServiceTest.java",
                "Flaky test: OrderPricingServiceTest.appliesDiscountWhenPriceIsReady", "d", Enums.Severity.MAJOR);
    }

    @Test
    void replacesSleepWithBoundedWaitAsValidDiff() {
        GeneratedFix fix = generator.build(new GenerationInput(flaky(), List.of(),
                "src/test/java/a/OrderPricingServiceTest.java", SOURCE));

        assertThat(fix.hasPatch()).isTrue();
        String patched = UnifiedDiff.apply(SOURCE, UnifiedDiff.parse(fix.patchDiff()).getFirst());
        assertThat(patched).doesNotContain("Thread.sleep").contains("price.get(5, java.util.concurrent.TimeUnit.SECONDS);");
        assertThat(fix.confidence()).isLessThan(0.8);
    }

    @Test
    void returnsNoPatchWhenSourceIsMissing() {
        GeneratedFix fix = generator.build(new GenerationInput(flaky(), List.of(), null, null));
        assertThat(fix.hasPatch()).isFalse();
        assertThat(fix.confidence()).isLessThan(0.5);
    }
}

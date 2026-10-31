package com.codehealth.agent.monitor;

import com.codehealth.agent.domain.Enums.TestOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JUnitXmlParserTest {

    private final JUnitXmlParser parser = new JUnitXmlParser();

    @Test
    void parsesPassFailSkip() {
        String xml = """
                <testsuite name="S">
                  <testcase classname="a.B" name="ok" time="0.010"/>
                  <testcase classname="a.B" name="bad" time="1.5"><failure message="boom"/></testcase>
                  <testcase classname="a.B" name="err" time="0"><error message="npe"/></testcase>
                  <testcase classname="a.B" name="skip"><skipped/></testcase>
                </testsuite>
                """;
        var tests = parser.parse(xml);
        assertThat(tests).extracting(JUnitXmlParser.ParsedTest::outcome)
                .containsExactly(TestOutcome.PASSED, TestOutcome.FAILED, TestOutcome.FAILED, TestOutcome.SKIPPED);
        assertThat(tests.get(1).durationMs()).isEqualTo(1500);
        assertThat(tests.get(1).failureMessage()).isEqualTo("boom");
    }

    @Test
    void rejectsExternalEntities() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>
                <testsuite><testcase classname="a" name="&e;"/></testsuite>
                """;
        assertThatThrownBy(() -> parser.parse(xxe)).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.codehealth.agent.monitor;

import com.codehealth.agent.domain.Enums.TestOutcome;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Surefire/Failsafe and Gradle JUnit XML reports. External entities and DTDs are disabled (XXE safe),
 * because the XML arrives over the network from CI.
 */
@Component
public class JUnitXmlParser {

    public record ParsedTest(String testClass, String testName, TestOutcome outcome, long durationMs,
                             String failureMessage) {
    }

    public List<ParsedTest> parse(String xml) {
        try {
            Document doc = newSecureBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList cases = doc.getElementsByTagName("testcase");
            List<ParsedTest> tests = new ArrayList<>(cases.getLength());
            for (int i = 0; i < cases.getLength(); i++) {
                Element testCase = (Element) cases.item(i);
                tests.add(toParsedTest(testCase));
            }
            return tests;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JUnit XML report: " + ex.getMessage(), ex);
        }
    }

    private static ParsedTest toParsedTest(Element testCase) {
        String className = testCase.getAttribute("classname");
        String name = testCase.getAttribute("name");
        long durationMs = parseSeconds(testCase.getAttribute("time"));

        Element failure = firstChild(testCase, "failure");
        if (failure == null) {
            failure = firstChild(testCase, "error");
        }
        if (failure != null) {
            String message = failure.getAttribute("message");
            return new ParsedTest(className, name, TestOutcome.FAILED, durationMs,
                    message.isBlank() ? failure.getTextContent() : message);
        }
        if (firstChild(testCase, "skipped") != null) {
            return new ParsedTest(className, name, TestOutcome.SKIPPED, durationMs, null);
        }
        return new ParsedTest(className, name, TestOutcome.PASSED, durationMs, null);
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }

    private static long parseSeconds(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.round(Double.parseDouble(value.replace(",", "")) * 1000);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static DocumentBuilder newSecureBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }
}

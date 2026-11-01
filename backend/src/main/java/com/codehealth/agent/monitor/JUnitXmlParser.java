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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: JUnitXmlParser.java
 *  Date: October 31, 2026
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
            Document document = newSecureBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList nodeList = document.getElementsByTagName("testcase");
            return IntStream.range(0, nodeList.getLength()).mapToObj(i -> (Element) nodeList.item(i)).map(JUnitXmlParser::toParsedTest).collect(Collectors.toCollection(() -> new ArrayList<>(nodeList.getLength())));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JUnit XML report: " + exception.getMessage(), exception);
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
        } catch (NumberFormatException numberFormatException) {
            return 0;
        }
    }

    private static DocumentBuilder newSecureBuilder() throws Exception {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        documentBuilderFactory.setXIncludeAware(false);
        documentBuilderFactory.setExpandEntityReferences(false);
        return documentBuilderFactory.newDocumentBuilder();
    }
}
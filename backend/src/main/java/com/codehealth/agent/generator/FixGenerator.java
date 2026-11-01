package com.codehealth.agent.generator;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: FixGenerator.java
 *  Date: October 31, 2026
 * Name recorded on the proposal and in the audit trail, e.g. "spring-ai:gpt-4o-mini" or "template".
 */
public interface FixGenerator {

    String name();

    GeneratedFix generate(GenerationInput input);
}
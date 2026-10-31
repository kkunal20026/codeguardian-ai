package com.codehealth.agent.generator;

public interface FixGenerator {

    /**
     * Name recorded on the proposal and in the audit trail, e.g. "spring-ai:gpt-4o-mini" or "template".
     */
    String name();

    GeneratedFix generate(GenerationInput input);
}

package com.codehealth.agent.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHasherTest {

    @Test
    void isDeterministicAndHex() {
        String a = AuditHasher.hash(AuditHasher.GENESIS, "E", "T", "1", "u", "{}", "2026-01-01T00:00:00Z");
        String b = AuditHasher.hash(AuditHasher.GENESIS, "E", "T", "1", "u", "{}", "2026-01-01T00:00:00Z");
        assertThat(a).isEqualTo(b).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void fieldBoundariesCannotBeShifted() {
        // With a plain "|" join these two inputs would hash identically.
        String a = AuditHasher.hash("p", "A|B", "C", "1", "u", "{}", "t");
        String b = AuditHasher.hash("p", "A", "B|C", "1", "u", "{}", "t");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void anyFieldChangeChangesHash() {
        String base = AuditHasher.hash("p", "E", "T", "1", "alice", "{\"x\":1}", "t");
        assertThat(AuditHasher.hash("p", "E", "T", "1", "mallory", "{\"x\":1}", "t")).isNotEqualTo(base);
        assertThat(AuditHasher.hash("p", "E", "T", "1", "alice", "{\"x\":2}", "t")).isNotEqualTo(base);
    }
}

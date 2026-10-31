-- V3: engineering knowledge used for retrieval-augmented generation.
-- Embeddings are computed at startup when an embedding model is configured.

INSERT INTO knowledge_doc (doc_key, content, tags, created_at) VALUES
('runbook-flaky-time',
 'Flaky tests that call Instant.now(), LocalDateTime.now() or System.currentTimeMillis() must receive a java.time.Clock. In tests use Clock.fixed(...) so assertions do not depend on wall-clock time.',
 'flaky,time,clock', SYSUTCDATETIME()),
('runbook-flaky-sleep',
 'Never wait for asynchronous work with Thread.sleep in a test. Join the CompletableFuture with a timeout, or poll with Awaitility (await().atMost(...).until(...)). Sleep-based waits fail under CI load.',
 'flaky,async,sleep,awaitility', SYSUTCDATETIME()),
('runbook-flaky-order',
 'Tests that pass alone but fail in the full suite usually share static state or rely on test execution order. Reset shared state in @BeforeEach and avoid static mutable fields.',
 'flaky,order,state', SYSUTCDATETIME()),
('runbook-flaky-random',
 'Tests using Random without a seed are non-deterministic. Inject a seeded Random or a RandomGenerator so the same input always gives the same result.',
 'flaky,random', SYSUTCDATETIME()),
('runbook-sonar-null',
 'For Sonar rule java:S2259 (null pointer dereference), add an explicit null check or use Optional at the boundary. Do not suppress the rule.',
 'sonar,null', SYSUTCDATETIME()),
('policy-auto-fix',
 'Automatic fixes are only allowed for test code. Production code, build files, configuration and database migrations always need a human reviewer.',
 'policy,risk', SYSUTCDATETIME());

# Phase 1: Testcontainers & Test Infrastructure Setup

## P1 Stories Enabled
No P1 story is directly implemented in this phase — it is the prerequisite plumbing Phase 2/3 need to write real (not mocked) repository tests. It unblocks:
- "As a maintainer, I want repository adapters verified against a real Postgres instance, so that Postgres-specific behavior (constraints, types, pagination) is actually exercised, not approximated by H2. Accepted when: integration tests for both repositories run via Testcontainers (`postgres:16-alpine`, matching `docker-compose.yml`)..." — this phase delivers the Testcontainers wiring and the shared base test class every later integration test extends.

## Requirements
`infrastructure/pom.xml` gains Testcontainers test-scope dependencies; a test-only minimal Spring Boot config class exists under `infrastructure/src/test/java` (never `bootstrap`) so `@DataJpaTest`-style tests have a `@SpringBootConfiguration` to discover; a shared base test class starts one `postgres:16-alpine` container (matching `docker-compose.yml`) via `@ServiceConnection`, started once and reused across the module's test suite. `testing: --tdd` — this phase is almost pure plumbing with one real behavior to pin: that the wiring actually produces a working database connection inside a Spring context, which is exactly what a "does it boot and can it query" smoke test verifies.

### Tests to Write First
- `PostgresContainerSmokeTest` (new, `infrastructure/src/test/java`): before any pom dependency, config class, or base class exists, write a test that extends the (not-yet-existing) shared base class, loads a minimal Spring context, and asserts a raw `SELECT 1` (or equivalent trivial query, e.g. via an injected `DataSource`/`JdbcTemplate`) succeeds against the container. This will not even compile at first (red) — implement Steps 1-4 below to make it pass (green).

## Files to Create/Modify
- MODIFY `infrastructure/pom.xml` — add `org.springframework.boot:spring-boot-testcontainers` (test scope) and `org.testcontainers:junit-jupiter` + `org.testcontainers:postgresql` (test scope); no explicit version pins — all three resolve via the `spring-boot-starter-parent:4.1.1` BOM already inherited from the root `pom.xml`.
- CREATE `infrastructure/src/test/java/com/tienphat/infrastructure/InfrastructureTestApplication.java` — a test-scope-only `@SpringBootApplication` (or equivalent `@Configuration` + `@EnableAutoConfiguration` + `@EntityScan` + `@EnableJpaRepositories` combination), scoped to `infrastructure`'s own packages. Exists solely so `@DataJpaTest`/`@SpringBootTest`-style tests in this module have a `@SpringBootConfiguration` to find — never referenced from `bootstrap`.
- CREATE `infrastructure/src/test/java/com/tienphat/infrastructure/AbstractPostgresIntegrationTest.java` — the shared base class every Phase 2/3 integration test extends: a `static` `PostgreSQLContainer<?>` using image `postgres:16-alpine`, started once (singleton pattern — no per-test restart), exposed to the Spring context via a `@ServiceConnection`-annotated static field/bean. Does not mix `@ServiceConnection` with `@Testcontainers`/`@Container` lifecycle annotations on the same field — one pattern, consistently.
- CREATE `infrastructure/src/test/java/com/tienphat/infrastructure/PostgresContainerSmokeTest.java` — the red-then-green test described above; extends `AbstractPostgresIntegrationTest`, annotated for Spring context loading, asserts a trivial query succeeds.

## Steps
1. Write `PostgresContainerSmokeTest` (red) — it references a base class and test-app class that don't exist yet, so this step is "write the test file", not "run it".
2. Add the three Testcontainers test-scope dependencies to `infrastructure/pom.xml`.
3. Create `InfrastructureTestApplication` as the module's test-only `@SpringBootConfiguration` source.
4. Create `AbstractPostgresIntegrationTest` with the singleton `postgres:16-alpine` container wired via `@ServiceConnection`.
5. Run `PostgresContainerSmokeTest` and confirm it turns green — Spring context loads, container starts exactly once, the trivial query succeeds.
6. Confirm no `@SpringBootApplication`/`@SpringBootConfiguration` was added anywhere under `infrastructure/src/main` or `bootstrap` — the test config stays test-scope-only.

## Success Criteria
- `./mvnw -pl infrastructure -am test` runs `PostgresContainerSmokeTest` successfully, with the Testcontainers Postgres container starting and the smoke query passing.
- `infrastructure/pom.xml`'s three new Testcontainers dependencies are all `test` scope — no leakage into `compile`/`runtime`.
- `grep -rln "SpringBootApplication\|SpringBootConfiguration" infrastructure/src` returns exactly one file, and it is under `infrastructure/src/test/java` — confirms the test-only Boot config never leaks into `infrastructure/src/main` or `bootstrap`.
- The base test class starts its container exactly once for the whole test run (singleton pattern) — verified by adding a second trivial test method/class in a later phase reusing the same base class without a second container startup log line.

## Risks
- Mixing `@ServiceConnection` with `@Testcontainers`/`@Container` on the same field is a documented conflict (competing lifecycle management) that can manifest as flaky or duplicate container starts rather than a clean failure. Mitigation: `AbstractPostgresIntegrationTest` uses `@ServiceConnection` exclusively, with the container's lifecycle managed manually (started once, statically) — no `@Testcontainers`/`@Container` annotations anywhere in this module's tests.
- CI/local environments without Docker available fail this entire phase (and therefore Phase 2/3) with an infrastructure-level error, not a code defect. Mitigation: this phase's own success criteria isolates that failure mode to a single, obvious smoke test before any real repository behavior is built on top of it.

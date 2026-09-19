# Phase 1: Domain Foundation & Module Wiring

## P1 Stories Enabled
No P1 story is directly implemented in this phase — it is the prerequisite plumbing every Phase 2/3 use-case needs. It unblocks:
- "As any user, I want to list Events with pagination so that I can browse available events. Accepted when: `ListEventsUseCase` returns a paged result using a newly added `EventRepository.findAll(...)` port method." — needs `PageRequest`/`PageResult` + the port method added here.
- "As an organizer, I want to view a single Event's details by id. Accepted when: `GetEventUseCase` returns the mapped `EventResult` or a not-found error when the id doesn't exist." — and the equivalent TicketType story — both need `EventNotFoundException`/`TicketTypeNotFoundException` added here.
- Every write use-case in Phase 2/3 needs the `UseCase<I, O>` interface and the `spring-tx`/MapStruct dependencies added here.

## Requirements
`domain` gains a pagination port contract and two not-found exceptions; `application`'s `pom.xml` gains exactly the two dependency families the spec allows (`spring-tx`, `mapstruct`/`mapstruct-processor`) plus test tooling; the shared `UseCase<I, O>` interface exists. No use-case code is written yet — this phase's outcome is verified by compilation, not behavior. `testing: --tdd` — this phase has almost no business behavior to drive with tests (it's plumbing/wiring), so TDD here is limited to `PageResult`'s computed helpers; everything else is verified by the compile/dependency gates in Success Criteria instead.

### Tests to Write First
- `PageResultTest` (new, `domain/src/test`): before writing `PageResult`, write the test cases for `totalPages()` (exact division, remainder rounds up, zero `totalElements`) and `hasNext()` (false on the last page, true mid-list, false when `totalElements == 0`). Run red, then implement `PageRequest`/`PageResult` (Step 1) to turn it green.
- No test precedes `EventRepository.findAll`'s signature addition or the two new exception classes — both are pure structural additions with no behavior to assert yet (their behavior is exercised by Phase 2/3's use-case tests instead).

## Steps
1. Write `PageResultTest` per the "Tests to Write First" section above (red), then add `PageRequest`/`PageResult` as new hand-rolled records in `domain.repository` to make it pass (green) — `PageRequest` carrying `page`/`size` (0-indexed) with an `offset()` helper; `PageResult<T>` carrying `content`/`page`/`size`/`totalElements` with `totalPages()`/`hasNext()` helpers. Zero Spring imports.
2. Extend `EventRepository` with a `findAll(PageRequest)` port method returning `PageResult<Event>`. Leave `save()`/`findById()` untouched — this is a pure addition.
3. Add `EventNotFoundException` and `TicketTypeNotFoundException` to `domain.exception`, each extending `DomainException` with the same single-message constructor every existing exception in that package uses.
4. Extend `application/pom.xml`: add `spring-tx` (version resolved via the `spring-boot-starter-parent` BOM, no explicit pin), `mapstruct`/`mapstruct-processor` (using the parent's existing `mapstruct.version` property), Lombok (`optional`, mirroring `domain/pom.xml`) plus `lombok-mapstruct-binding` (using the parent's existing `lombok-mapstruct-binding.version` property), and wire the annotation-processor chain (`lombok-mapstruct-binding` → `lombok` → `mapstruct-processor`) on the compiler plugin.
5. Add JUnit 5, AssertJ, and a mocking library (Mockito) as `test`-scope dependencies to `application/pom.xml` — none exist there today, and Phase 2/3 use-case tests mock `EventRepository`/`TicketTypeRepository`/mappers.
6. Create `application/src/main/java/com/tienphat/application/usecase/UseCase.java`: a generic interface with a single `O execute(I input)` method.
7. Verify `application` compiles standalone with only `UseCase.java` present, confirming the dependency/annotation-processor wiring before any mapper or use-case is written against it.

## Success Criteria
- `mvnw -pl domain test` passes, including the new `PageResult` helper tests.
- `mvnw -pl application compile` succeeds with the new `pom.xml` dependencies and exactly one source file (`UseCase.java`).
- `grep -rn "org.springframework\|javax.persistence\|jakarta.persistence" domain/src/main/java` returns zero matches — `domain` stays a plain POJO module.
- `application/pom.xml`'s dependency list beyond `domain` is exactly `spring-tx`, `mapstruct`, `mapstruct-processor`, Lombok, `lombok-mapstruct-binding`, and the three test-scope libraries — no `spring-web`, `spring-security`, or JPA/JDBC dependency.
- `EventRepository` compiles with the new `findAll(PageRequest)` method; no existing caller breaks (none exist yet, per spec Success Criteria).

## Risks
- Getting the MapStruct/Lombok annotation-processor order wrong: mitigate by treating "an empty `@Mapper`-free `application` module compiles" as this phase's own gate, and re-verifying compilation the moment Phase 2 adds the first real `@Mapper` interface.
- `PageRequest`/`PageResult` indexing convention drifting once a real Spring Data adapter is built in `infrastructure`: mitigate by documenting the 0-indexed convention directly in the record's Javadoc, not just in this plan.

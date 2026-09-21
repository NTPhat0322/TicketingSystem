# Spec: Infrastructure Layer — Event & TicketType Persistence (P1)

**Date:** 2026-09-21
**Status:** Ready

---

## Problem Statement

The `application` module's Event/TicketType use-cases (P1, already complete) depend on `EventRepository`/`TicketTypeRepository` ports that have no implementation — the `infrastructure` module only has a scaffolded `pom.xml` (Spring Data JPA, Postgres driver, MapStruct) with an empty `src`. Without a real adapter, the use-cases can't run against an actual database, and `bootstrap` can't start a fully wired application.

---

## User Stories

- **[P1]** As the application layer, I want `EventRepository.save()`/`findById()`/`findAll(PageRequest)` backed by a real Postgres-persisted `Event`, so that `CreateEventUseCase`, `UpdateEventUseCase`, `GetEventUseCase`, `ListEventsUseCase`, and `DeactivateEventUseCase` work end-to-end.
  Accepted when: an `EventRepository` Spring bean exists in `infrastructure`, backed by `EventJpaEntity` + Spring Data JPA, and every existing application-layer Event use-case test still passes unmodified against it in an integration test.

- **[P1]** As the application layer, I want `TicketTypeRepository.save()`/`findById()`/`findAllByEventId()` backed by a real Postgres-persisted `TicketType`, so that all 5 TicketType use-cases work end-to-end.
  Accepted when: a `TicketTypeRepository` Spring bean exists in `infrastructure`, backed by `TicketTypeJpaEntity` + Spring Data JPA.

- **[P1]** As a maintainer, I want domain aggregates to stay JPA-free, so that `domain` keeps compiling as a plain POJO module with no framework dependency.
  Accepted when: `Event`/`TicketType` in `domain` receive no new annotations; all JPA mapping lives in `infrastructure`-only `EventJpaEntity`/`TicketTypeJpaEntity` + MapStruct mappers using each aggregate's existing `reconstitute(...)` factory.

- **[P1]** As a maintainer, I want a concurrent `TicketType` update (stale `version`) to surface as a domain exception, so that `application`/`presentation` never need to know about Hibernate's exception types.
  Accepted when: `TicketTypeJpaEntity` has `@Version int version`; the repository adapter catches `ObjectOptimisticLockingFailureException` on save and rethrows a new `TicketTypeConcurrentUpdateException` (domain-owned).

- **[P1]** As a maintainer, I want repository adapters verified against a real Postgres instance, so that Postgres-specific behavior (constraints, types, pagination) is actually exercised, not approximated by H2.
  Accepted when: integration tests for both repositories run via Testcontainers (`postgres:16-alpine`, matching `docker-compose.yml`), covering save/findById/findAll/findAllByEventId happy paths plus the optimistic-lock conflict path for `TicketType`.

- **[P3]** _(out of scope)_ Redis stock cache / `StockCachePort` — tied to Order flow, not this phase.
- **[P3]** _(out of scope)_ Outbox persistence — no `OutboxEventType` exists for Event/TicketType yet.
- **[P3]** _(out of scope)_ Presentation layer (REST controllers, DTOs, exception mapping) — tracked as a separate, independent spec per explicit user decision.
- **[P3]** _(out of scope)_ Flyway/Liquibase migrations — `ddl-auto: update` is the deliberate choice for this phase.

---

## Functional Requirements

1. FR-01: `EventJpaEntity` and `TicketTypeJpaEntity` are new `@Entity` classes in `infrastructure`, structurally mirroring `Event`/`TicketType`'s fields, with no behavior beyond persistence mapping.
2. FR-02: `EventPersistenceMapper` and `TicketTypePersistenceMapper` (MapStruct, `componentModel = "spring"`) convert entity → domain via each aggregate's `reconstitute(...)` static factory, and domain → entity via plain getters — no business logic in the mapper.
3. FR-03: `Money ↔ BigDecimal` conversion in `TicketTypePersistenceMapper` reuses the same custom-method pattern already validated in `application`'s `TicketTypeMapper` (`default BigDecimal map(Money price)` / the reverse).
4. FR-04: Spring Data JPA repository interfaces (`EventJpaRepository extends JpaRepository<EventJpaEntity, UUID>`, `TicketTypeJpaRepository extends JpaRepository<TicketTypeJpaEntity, UUID>` with a derived `findAllByEventId(UUID)`) are infra-internal — never exposed outside `infrastructure`.
5. FR-05: `EventRepositoryImpl`/`TicketTypeRepositoryImpl` (Spring `@Repository` beans) implement the domain ports (`EventRepository`, `TicketTypeRepository`) by delegating to the Spring Data interfaces + mapper — these are the only classes `application` ever sees via the port.
6. FR-06: `EventRepositoryImpl.findAll(PageRequest)` converts domain `PageRequest` → Spring `Pageable` and Spring `Page<EventJpaEntity>` → domain `PageResult<Event>` — pagination happens at the query level (`Pageable`), never as an in-memory slice.
7. FR-07: `TicketTypeJpaEntity` declares `@Version int version` mapped 1:1 to the domain's existing `version` field (used for optimistic locking, not touched by application-layer code directly).
8. FR-08: A new domain exception `TicketTypeConcurrentUpdateException` (in `domain.exception`) is thrown by `TicketTypeRepositoryImpl.save()` when Spring Data throws `ObjectOptimisticLockingFailureException` — no Spring ORM exception type crosses the port boundary.
9. FR-09: No schema-migration tool (Flyway/Liquibase) is introduced — `spring.jpa.hibernate.ddl-auto=update` (already configured in `bootstrap/src/main/resources/application.yml`) is the schema source for this phase.
10. FR-10: Repository integration tests use Testcontainers' Postgres module (`postgres:16-alpine`, matching `docker-compose.yml`'s image) — not H2, not mocks.

---

## Non-Functional Requirements

- Performance: `EventRepositoryImpl.findAll(...)` must issue a single paginated SQL query (`LIMIT`/`OFFSET` via `Pageable`) — no `findAll()` + in-memory sublist.
- Security: none owned by this layer (unchanged from the application-layer spec — authN/authZ is presentation's job).
- Maintainability: `domain` gains exactly one new type (`TicketTypeConcurrentUpdateException`) and no new dependency. `infrastructure` is the only module allowed to depend on `spring-boot-starter-data-jpa`/`postgresql`/Testcontainers.

---

## Success Criteria

- [ ] Both `EventRepository` and `TicketTypeRepository` are implemented and registered as Spring beans; every existing application-layer use-case (10 total) runs correctly against them in an integration test, unmodified from their current unit-test form.
- [ ] Repository integration tests (Testcontainers Postgres) cover save/findById/findAll/findAllByEventId for both aggregates, plus the `TicketType` optimistic-lock conflict path — 100% pass.
- [ ] A stale-`version` concurrent `TicketType` save surfaces as `TicketTypeConcurrentUpdateException`, never as a raw `ObjectOptimisticLockingFailureException`, verified by an integration test that forces two concurrent saves.
- [ ] `./mvnw -pl infrastructure -am test` passes with zero Flyway/Liquibase dependency added and zero new annotations on `domain.model.Event`/`domain.model.TicketType`.

---

## Out of Scope

- Order, Payment, Ticket, User persistence (next phases).
- Redis stock cache / `StockCachePort`.
- Outbox event persistence for Event/TicketType.
- Presentation layer — REST controllers, request/response DTOs, `@ControllerAdvice` exception mapping (separate spec, per explicit user decision to split infra and presentation into two independent specs).
- Schema migration tooling (Flyway/Liquibase) — deferred indefinitely per user's `ddl-auto` choice for this phase.
- Optimistic locking for `Event` — the domain model has no `version` field on `Event` today; adding one is a domain change, not an infra one, and is not part of this spec.

---

## Assumptions

- `EventJpaEntity`/`TicketTypeJpaEntity` use assigned (not generated) UUID primary keys — ids are already generated at the application/use-case layer before `TicketType.create(id, ...)`/`Event.create(id, ...)` is called.
- The existing `docker-compose.yml` Postgres service (`db`, `postgres:16-alpine`) is for local/dev runtime; Testcontainers spins up its own ephemeral instance for tests, independent of that compose service.
- `EventJpaEntity`/`TicketTypeJpaEntity` mapping is 1:1 with domain fields — no denormalization or extra columns beyond what `Event`/`TicketType` already expose.
- MapStruct mapper tests follow the same non-mocked, `Mappers.getMapper(...)`-based pattern already used for `EventMapperTest`/`TicketTypeMapperTest` in `application`.

---

# Brainstorm: Infrastructure Layer (Event & TicketType persistence)

**Date:** 2026-09-21

## Ideas Explored

- **Schema management**: Flyway/Liquibase vs Hibernate `ddl-auto`. Repo already has `ddl-auto: update` wired in `bootstrap/src/main/resources/application.yml` from an earlier session — user confirmed sticking with it, no migration tool for this phase.
- **Entity strategy**: `@Entity` directly on `Event`/`TicketType` (fewer files, but leaks JPA into `domain`) vs separate `EventJpaEntity`/`TicketTypeJpaEntity` + bidirectional MapStruct mapper (mirrors the pattern already validated for `EventMapper`/`TicketTypeMapper` in the application layer). User chose the separate-entity approach — domain stays a plain POJO module.
- **Repository test strategy**: H2 in-memory (fast, but diverges from Postgres-specific behavior) vs Testcontainers with real Postgres. User chose Testcontainers — repo already runs Postgres 16-alpine via `docker-compose.yml` (from `feature/dockerize-project`), so this is consistent with how the app runs in every other environment.
- **Optimistic-lock conflict handling**: `TicketType.version` (`int`) exists in the domain today but no domain exception represents a concurrency conflict. Two options surfaced:
  - (a) new domain exception (e.g. `TicketTypeConcurrentUpdateException`), infra adapter catches Hibernate's `ObjectOptimisticLockingFailureException` and translates it — keeps `domain`/`application` free of Spring ORM types.
  - (b) let `ObjectOptimisticLockingFailureException` propagate straight up — less code, but `application` (or its callers) would end up branching on a Spring ORM exception type, breaking the dependency-inversion the rest of the module already enforces.
  User picked **(a)**.
- **Scope boundary**: whether to fold in Redis stock cache / Outbox persistence (both previously marked out-of-scope in the application-layer spec) — dismissed; this phase stays strictly matched to the application layer's existing P1 ports (`EventRepository`, `TicketTypeRepository`).
- **Presentation layer**: originally bundled into this brainstorm's ask ("làm tiếp tầng infrastructure và presentation"), but user explicitly sequenced infra first and asked for it as **two independent specs** rather than one combined plan.

## User's Direction

Infra first, scoped exactly to what the application layer's P1 use-cases already need (`EventRepository`, `TicketTypeRepository`). No new persistence framework decisions beyond what's already scaffolded (`spring-boot-starter-data-jpa`, Postgres, `ddl-auto: update`). Domain stays framework-free — JPA entities and mapping are infra-only concerns. Optimistic locking on `TicketType` gets a proper domain-level exception instead of leaking a Hibernate type across the port boundary. Presentation layer is deliberately deferred to a separate brainstorm/spec.

## Open Questions

- Exact name and package for the new concurrency-conflict domain exception (e.g. `TicketTypeConcurrentUpdateException` in `domain.exception`) — left for `/ck:plan` to finalize alongside the phase breakdown.
- Testcontainers dependency versions and whether integration tests share one `@Testcontainers` base class across both repositories — implementation detail, not spec-blocking.

## Risks

- `ddl-auto: update` is a known footgun long-term (silent, uncontrolled schema drift) — acceptable now since the project has no production data yet, but worth revisiting before a real deployment.
- `Event` has no `version` field in the domain model today, so it gets no optimistic locking in this phase — only `TicketType` does. If concurrent Event updates become a real concern later, that's a separate domain change, not an infra one.

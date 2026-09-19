# Spec: Application Layer — Event & TicketType Use Cases (P1)

**Date:** 2026-09-18
**Status:** Ready

---

## Problem Statement

Domain layer (aggregates, value objects, repository ports) is complete. The `application` module currently has no code — only a dependency on `domain`. Organizers need a way to create and manage Events and TicketTypes before any Order/Payment/Ticket flow can exist, so the application layer's first slice orchestrates that CRUD through use-cases that call domain aggregates and ports.

---

## User Stories

- **[P1]** As an organizer, I want to create a new Event so that it exists in `DRAFT` status and I can start configuring ticket types.
  Accepted when: `CreateEventUseCase` persists a new `Event` via `EventRepository.save()` and returns its id.

- **[P1]** As an organizer, I want to update an existing Event's details so that I can fix mistakes or adjust schedule before it goes on sale.
  Accepted when: `UpdateEventUseCase` loads the `Event` by id, applies changes, and saves — throwing `InvalidEventStateException` if `status != DRAFT`. No field is editable once the event has left `DRAFT`.

- **[P1]** As an organizer, I want to view a single Event's details by id.
  Accepted when: `GetEventUseCase` returns the mapped `EventResult` or a not-found error when the id doesn't exist.

- **[P1]** As any user, I want to list Events with pagination so that I can browse available events.
  Accepted when: `ListEventsUseCase` returns a paged result using a newly added `EventRepository.findAll(...)` port method.

- **[P1]** As an organizer, I want to deactivate an Event so that it stops being sellable.
  Accepted when: `DeactivateEventUseCase` calls `Event.cancel()` and saves — surfacing `InvalidEventStateException` if the current status cannot transition to `CANCELLED`.

- **[P1]** As an organizer, I want to create a TicketType under an Event so that I can define a price tier and inventory.
  Accepted when: `CreateTicketTypeUseCase` persists a new `TicketType` (status `ACTIVE`, `soldQuantity = 0`) via `TicketTypeRepository.save()`.

- **[P1]** As an organizer, I want to update a TicketType's details (name, price, quantities, hold duration) so that I can correct configuration before/while selling.
  Accepted when: `UpdateTicketTypeUseCase` loads by id, applies changes, and saves — without touching `soldQuantity`, `status`, or `version` directly (those are domain-owned transitions).

- **[P1]** As an organizer, I want to view a single TicketType by id.
  Accepted when: `GetTicketTypeUseCase` returns the mapped `TicketTypeResult` or a not-found error.

- **[P1]** As an organizer, I want to list all TicketTypes for an Event.
  Accepted when: `ListTicketTypesByEventUseCase` calls `TicketTypeRepository.findAllByEventId()` and returns mapped results.

- **[P1]** As an organizer, I want to deactivate a TicketType so that it stops accepting sales.
  Accepted when: `DeactivateTicketTypeUseCase` calls `TicketType.close()` and saves — surfacing `TicketTypeNotAvailableException` if the transition is invalid.

- **[P2]** As an organizer, I want to filter the Event list by my own organizerId.
  _(Deferred — P1 ships a public `findAll()`; organizer-scoped filtering can be added as an optional query param later without breaking the port signature.)_

- **[P3]** _(out of scope)_ Order, Payment, Ticket issuance/check-in use-cases — next phase.
- **[P3]** _(out of scope)_ Outbox event emission for Event/TicketType changes — `OutboxEventType` is currently closed to `ORDER_CREATED`/`ORDER_EXPIRED`/`PAYMENT_SUCCESS` only; no aggregate type exists for `Event`/`TicketType`.
- **[P3]** _(out of scope)_ Authorization/role checks — handled entirely in the presentation layer (`@PreAuthorize` or equivalent), not in use-cases.

---

## Functional Requirements

1. FR-01: Each use-case is a class implementing a generic `UseCase<I, O>` interface (single `execute(I input)` method), with its own `Command`/`Query` input type and `Result` output type.
2. FR-02: Use-cases call domain aggregate factory/mutator methods (`Event.create`, `Event.cancel`, `TicketType.create`, `TicketType.close`, etc.) — no business rule is re-implemented in the application layer.
3. FR-03: Use-cases depend only on repository ports already defined in `domain.repository` (`EventRepository`, `TicketTypeRepository`), extended where needed (see FR-04).
4. FR-04: `EventRepository` gains a new port method `findAll(PageRequest)` returning a `PageResult<Event>`, to support `ListEventsUseCase`. Both `PageRequest` and `PageResult` are new, hand-rolled types in `domain` (no Spring Data dependency) — `domain` stays a plain POJO module. Infrastructure converts to/from Spring Data's `Pageable`/`Page<T>` internally when implementing the port.
5. FR-05: Each write use-case (`Create*`, `Update*`, `Deactivate*`) is annotated `@Transactional` at the use-case class or method level.
6. FR-06: Mapping between domain model and Command/Result DTOs is done via MapStruct mappers (one mapper per aggregate: `EventMapper`, `TicketTypeMapper`).
7. FR-07: Use-cases throw domain exceptions (`InvalidEventDataException`, `InvalidEventStateException`, `InvalidTicketTypeDataException`, `TicketTypeNotAvailableException`, etc.) directly — no catching/wrapping into a Result/Either type.
8. FR-08: Use-cases accept already-validated input (field-level validation, e.g. `@NotBlank`, `@Positive`, is the presentation layer's responsibility via Bean Validation on request DTOs, not re-done here).
9. FR-09: No hard-delete use-case exists for Event or TicketType. "Deactivate" is the only removal-adjacent operation, implemented via `Event.cancel()` / `TicketType.close()`.

---

## Non-Functional Requirements

- Performance: `ListEventsUseCase` must not load the full table into memory — pagination is enforced at the repository/query level, not in-memory slicing.
- Security: none owned by this layer — authentication/authorization is presentation's responsibility (see Out of Scope).
- Maintainability: `application` module dependency graph after this phase = `domain` + `spring-tx` (for `@Transactional`) + `mapstruct`. No `spring-web`, `spring-security`, or persistence (JPA/JDBC) dependency.

---

## Success Criteria

- [ ] All 10 P1 use-cases compile and have unit tests covering: happy path, and each domain exception path reachable from that use-case.
- [ ] `application` module's `pom.xml` only adds `spring-tx` and `mapstruct`/`mapstruct-processor` beyond the existing `domain` dependency — no web/persistence framework leaks in.
- [ ] `EventRepository.findAll(...)` addition does not break existing `save()`/`findById()` callers (none exist yet, so this is a pure addition).
- [ ] Every write use-case's `@Transactional` boundary wraps exactly one aggregate save (no cross-aggregate transaction spanning Event + TicketType in a single use-case for this phase).

---

## Out of Scope

- Order, Payment, Ticket use-cases (next phase).
- Outbox event emission for Event/TicketType (no `OutboxEventType`/`AggregateType` values exist for them yet).
- Authorization/role-based access control (presentation layer's job).
- Redis stock warming / `StockCachePort` orchestration (tied to Order flow, not Event/TicketType CRUD).
- Hard-delete of Event or TicketType.

---

## Assumptions

- The `UseCase<I, O>` generic interface lives in `application` (a new shared file, e.g. `application/usecase/UseCase.java`), not in `domain`.
- Package organization inside `application` follows an aggregate-first layout (e.g. `application/event/...`, `application/tickettype/...`), matching the domain module's per-aggregate-file style — final layout decided at `/ck:plan` time, not spec-blocking.
- `TicketType.create` requires `eventId` to reference an existing Event; `CreateTicketTypeUseCase` is responsible for verifying the Event exists (via `EventRepository.findById`) before calling `TicketType.create`, since `TicketType` is its own aggregate root and does not validate this itself.

---

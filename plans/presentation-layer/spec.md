# Spec: Presentation Layer — REST API for Event & TicketType

**Date:** 2026-09-21
**Status:** Draft

---

## Problem Statement

The domain, application, and infrastructure layers are complete (10 use-cases for Event and TicketType, backed by real JPA persistence), but nothing exposes them over HTTP yet. The presentation layer must wrap all 10 use-cases in REST controllers with request validation, DTO mapping, and centralized error handling, so external clients can create/manage events and ticket types.

---

## User Stories

<!-- P1 = MVP (must ship), P2 = nice-to-have, P3 = future/out-of-scope -->

- **[P1]** As an API client, I want to create/update/get/list/deactivate an Event via REST so that I can manage events without touching the database directly.
  Accepted when: all 5 Event endpoints (`POST`, `PUT`, `GET /{id}`, `GET` list, `POST /{id}/deactivate` or equivalent) return correct status codes and bodies against real Postgres.

- **[P1]** As an API client, I want to create/update/get/list/deactivate a TicketType via REST so that I can manage ticket tiers under an event.
  Accepted when: all 5 TicketType endpoints work end-to-end, including the `eventId` existence check surfacing as 404 when the event doesn't exist.

- **[P1]** As an API client, I want invalid input (missing required fields, negative price/quantity, malformed UUIDs) rejected with a 400 and a structured error body, before it ever reaches a use-case.
  Accepted when: Jakarta Bean Validation annotations on request DTOs cause `@Valid` failures to short-circuit into a 400 `ProblemDetail`.

- **[P1]** As an API client, I want every domain exception (not-found, concurrent update, invalid state/data) translated into a consistent RFC 7807 `ProblemDetail` response, not a raw stack trace or generic 500.
  Accepted when: a single `@RestControllerAdvice` maps each relevant domain exception to the correct HTTP status and `ProblemDetail` body.

- **[P3]** _(out of scope — authentication/authorization, to be a separate future spec/pipeline)_

---

## Functional Requirements

1. FR-01: `POST /api/v1/events` — create an Event from a validated request DTO, mapped to `CreateEventCommand`, returns `201` with the created `EventResult` mapped to a response DTO.
2. FR-02: `PUT /api/v1/events/{id}` — update an Event, mapped to `UpdateEventCommand`, returns `200` with updated resource; `404` if event doesn't exist.
3. FR-03: `GET /api/v1/events/{id}` — fetch a single Event, `200` or `404`.
4. FR-04: `GET /api/v1/events` — paginated list of Events (query params `page`, `size`), maps `PageResult<EventResult>` to a paginated response envelope.
5. FR-05: `POST /api/v1/events/{id}/deactivate` — deactivate (cancel) an Event, `200` with updated status, `404` if not found.
6. FR-06: `POST /api/v1/ticket-types` — create a TicketType under an `eventId`, `201`; `404` if the referenced event doesn't exist (via `EventNotFoundException`).
7. FR-07: `PUT /api/v1/ticket-types/{id}` — update a TicketType, `200`; `404` if not found; `409` on optimistic-lock conflict (`TicketTypeConcurrentUpdateException`).
8. FR-08: `GET /api/v1/ticket-types/{id}` — fetch a single TicketType, `200` or `404`.
9. FR-09: `GET /api/v1/events/{eventId}/ticket-types` — list TicketTypes for an event.
10. FR-10: `POST /api/v1/ticket-types/{id}/deactivate` — close a TicketType, `200`; `404` if not found.
11. FR-11: A single `@RestControllerAdvice` (e.g. `GlobalExceptionHandler`) maps: `EventNotFoundException`/`TicketTypeNotFoundException` → 404, `TicketTypeConcurrentUpdateException` → 409, `Invalid*DataException`/`Invalid*ScheduleException`/`Invalid*StateException`/`TicketTypeNotAvailableException` → 400 or 422 (decided per-exception during planning), `MethodArgumentNotValidException` (from `@Valid` failures) → 400, unhandled `DomainException`/generic `Exception` → 500 fallback — all as RFC 7807 `ProblemDetail` bodies.
12. FR-12: All request DTOs carry Jakarta Bean Validation annotations matching domain invariants already enforced downstream (e.g. `@NotBlank` name, `@Positive` price/quantity, `@Future` sale/event dates where applicable).
13. FR-13: All Request↔Command and Result↔Response mappings go through MapStruct mapper interfaces (`Mappers.getMapper(...)` pattern), mirroring the infrastructure layer's mapper style.

---

## Non-Functional Requirements

- Performance: no new N+1 or blocking calls introduced at the HTTP boundary beyond what the use-cases already do.
- Security: no authentication/authorization in this phase (explicitly deferred — see Out of Scope). Endpoints must not be deployed to a public/production environment until that lands.
- Availability: N/A (single-instance dev scope, same as current infra layer).

---

## Success Criteria

- [ ] All 10 endpoints (5 Event + 5 TicketType) implemented and covered by integration tests hitting a real Spring context (mirroring `EventUseCaseIntegrationTest`/`TicketTypeUseCaseIntegrationTest` style, but through `MockMvc`/`WebTestClient` instead of calling use-cases directly).
- [ ] 100% of domain exceptions relevant to Event/TicketType flows are mapped in `GlobalExceptionHandler` — no unmapped exception reaches the client as a raw 500 with a stack trace.
- [ ] Invalid input (missing/malformed fields) is rejected with 400 before any use-case executes, verified by at least one negative test per request DTO.
- [ ] Full existing test suite (255 tests as of infra-layer completion) plus new presentation tests all pass, 0 failures.

---

## Out of Scope

- Authentication/authorization (API key, JWT, Basic Auth, or any Spring Security config) — deferred to a separate future spec/pipeline per explicit user decision.
- Any aggregate beyond Event/TicketType (Order, Payment, Ticket, User) — not yet implemented in domain/application layers, so no controllers for them here.
- API documentation content beyond what Springdoc/OpenAPI auto-generates from the existing `OpenApiConfig` bean (already present, untouched by this spec).

---

## Assumptions

- The existing `presentation/src/main/java/com/tienphat/presentation/config/OpenApiConfig.java` scaffold stays as-is; this spec only adds controllers, DTOs, mappers, and the exception handler alongside it.
- `bootstrap`'s `Application.java` already has `scanBasePackages = "com.tienphat"`, so new `@RestController`/`@RestControllerAdvice` beans in `presentation` will be auto-discovered without further wiring changes.
- Pagination response envelope shape and the exact 400-vs-422 split for domain validation exceptions are decided at `/ck:plan` time, not blocking this spec (marked as open questions in the brainstorm report, not `[NEEDS CLARIFICATION]` here since they don't change the spec's shape — just the phase-level detail).

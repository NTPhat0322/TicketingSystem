# Brainstorm: Presentation Layer (REST API for Event & TicketType)

**Date:** 2026-09-21

## Ideas Explored

- **DTO mapping approach**: MapStruct (reuse infra pattern) vs. manual mapping. User picked MapStruct — consistent with `infrastructure`'s `EventPersistenceMapper`/`TicketTypePersistenceMapper` pattern (`@ObjectFactory` not needed here since DTOs are plain records, not domain aggregates).
- **Exception handling**: single `@RestControllerAdvice` was a given from the start; the open question was error body shape — custom DTO vs. Spring Boot 3's built-in RFC 7807 `ProblemDetail`. User picked `ProblemDetail` — standard, zero custom format to design/maintain.
- **Input validation**: Jakarta Bean Validation (`@Valid` + annotations on request DTOs) vs. relying solely on existing application/domain validation. User picked Jakarta Bean Validation for fail-fast at the HTTP boundary; domain/application validation stays as defense-in-depth (unchanged).
- **Endpoint scope**: full 10 endpoints (mirrors the 10 existing use-cases: 5 Event + 5 TicketType) vs. a partial slice. User picked full scope.
- **Auth/authz**: initially user said "have basic Spring Security now" (API key / JWT / Basic Auth were the sub-options offered), then reconsidered and explicitly deferred all authentication/authorization to a future, separate pipeline. Endpoints in this spec are unauthenticated.
- **Route convention**: versioned (`/api/v1/...`) vs. unversioned. User picked versioned, kebab-case for multi-word resources (`/api/v1/ticket-types`).

## User's Direction

REST controllers for Event and TicketType exposing all 10 existing application use-cases, with a full request pipeline: `@Valid` request DTOs (Jakarta Bean Validation) → MapStruct mapping to application Command → use-case execution → MapStruct mapping of Result back to response DTO, and a single centralized `@RestControllerAdvice` translating domain exceptions to RFC 7807 `ProblemDetail` responses. No authentication/authorization in this phase — that's explicitly future, separate work (matching the earlier decision to keep presentation split from other concerns as independent specs/pipelines).

## Open Questions

- Exact HTTP status mapping per domain exception (e.g. `*NotFoundException` → 404, `TicketTypeConcurrentUpdateException` → 409, `Invalid*DataException`/`Invalid*ScheduleException`/`Invalid*StateException` → 400/422, unhandled `DomainException` → 500 fallback) — left for `/ck:plan` to finalize per-exception in the phase breakdown.
- Pagination response shape for `GET /api/v1/events` (wraps `PageResult<EventResult>`) — needs a concrete JSON shape (content/page/size/totalElements) decided during planning.
- Whether `TicketTypeConcurrentUpdateException` (optimistic lock) should surface a `version` field in the response so clients can retry with the correct version — worth deciding before implementing `UpdateTicketTypeUseCase`'s controller.

## Risks

- **Exception-to-status mapping drift**: as new domain exceptions get added later (Order/Payment/Ticket aggregates), the `@RestControllerAdvice` must be extended consistently or unmapped exceptions will leak as generic 500s.
- **DTO/Command field drift**: MapStruct mappers between presentation DTOs and application Commands/Results must be kept in sync manually whenever a Command/Result record changes shape (same risk class already accepted for the infra-layer persistence mappers).
- **Auth deferred**: shipping unauthenticated write endpoints (`POST`/`PUT`/`DELETE`-equivalents) is acceptable only because this stays internal/pre-production; must not be exposed publicly until the auth pipeline lands.

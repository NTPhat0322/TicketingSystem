# Phase 9: Start-sale and admin reservation-relay API operations

## Scope

Expose the two approved operational actions without changing the asynchronous purchase workflow:

- `POST /api/v1/events/{id}/start-sale`: ADMIN or the Event-owning ORGANIZER warms every TicketType's Redis stock before the Event enters `ON_SALE`.
- `POST /api/v1/admin/reservations/relay`: ADMIN runs one configured batch of due reservation intents through the existing relay. The response reports the number published with broker confirmation.

The scheduled relay remains the normal path. Existing claims, publisher confirms, retry backoff, and reconciliation remain authoritative.

## Implementation

- [x] Wire `StartSaleUseCase` into `EventController` with role and ownership enforcement.
- [x] Validate the Event lifecycle before Redis warming so a repeated request cannot reinitialize missing stock for an already-selling Event.
- [x] Add an application relay port/use case with an ADMIN authorization check.
- [x] Adapt the existing infrastructure relay and add the protected admin endpoint/response.
- [x] Keep the relay endpoint conditional on messaging/relay feature flags.
- [x] Update OpenAPI, API tests, the spec, and feature plan.

## Verification

- [x] Application relay use-case tests cover ADMIN success and non-admin rejection.
- [x] Docker-backed endpoint tests cover start-sale ownership, role checks, inventory warming, relay publishing, authorization, repeat trigger behavior, and OpenAPI Bearer metadata.
- [x] Docker-backed feature E2E: 7 tests passed.
- [x] Full reactor: 512 tests passed; 0 failures, errors, or skips.
- [x] `git diff --check` passes.

## Error Report

- Initial red checks were expected: test compilation first lacked the relay application types, and the start-sale route returned HTTP 404 before controller wiring.
- The first full-reactor run after controller wiring had 16 MVC context errors because `EventControllerTest` lacked a `StartSaleUseCase` mock. Adding `@MockitoBean` fixed the test context; the final full reactor passes.
- One full-reactor run hit a timing-sensitive existing `OrderExpiryMessagingIntegrationTest` assertion (stock expected 2, observed 3 with a two-second Rabbit TTL). The case passed in isolation and on the subsequent full-reactor rerun; no failure remains reproduced.
- Code review found and fixed a start-sale retry edge case: an already-`ON_SALE` Event is rejected before any Redis warm-up, preventing accidental stock reinitialization if its Redis key is missing.
- Review residual risk: manual relay is synchronous and waits for publisher confirms for one configured batch, so a slow broker may exceed HTTP client/proxy timeouts. Scheduled relay/retry remains the normal path; consider an asynchronous operations contract before production use.

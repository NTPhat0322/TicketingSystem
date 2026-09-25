# Phase 8: Full Docker-backed E2E, concurrency, and failure verification

## P1 Stories Satisfied

All P1 stories are verified together against real PostgreSQL, Redis, RabbitMQ, and the Spring HTTP/security stack.

## Affected Areas

- `bootstrap/src/test/java/com/tienphat/ticketingsystem/`
- `bootstrap/src/test/resources/application.yml`
- `infrastructure/src/test/java/` and test container base classes as needed
- `plans/ticket-purchase-hold/plan.md` Session Notes after completion

## Requirements

1. Add a full-context smoke test that creates/seeds an authenticated user, Event, TicketType, and warmed Redis inventory, then exercises reservation → `CREATING` → worker-created Order → polling → payment callback → `PAID`/Tickets.
2. Add a high-contention reservation test with stock `N` and more than `N` concurrent HTTP requests. Assert accepted reservations are at most `N`, Redis stock never goes negative, and Orders are unique.
3. Add a duplicate-delivery test that publishes the same order-create message several times and verifies one Order/OrderItem set.
4. Add a publish-failure/retry test that interrupts or rejects RabbitMQ publication after Redis reservation, then verifies retry or eventual release through reconciliation.
5. Add expiry/payment race tests and duplicate callback tests through the real application stack.
6. Validate Swagger/OpenAPI includes Bearer authentication and the new protected order/payment operations.
7. Run domain purity and raw-secret/credential checks, then the full reactor test suite.

## Implementation Steps

1. Extend the existing PostgreSQL integration base or create a feature base that starts all three dependencies once per test suite.
2. Add deterministic seed helpers for User/Event/TicketType and Redis warm-up.
3. Write the end-to-end lifecycle test using MockMvc/full Spring context, reusing the existing JWT test helpers.
4. Add concurrent request execution with `ExecutorService`/`CompletableFuture`; assert both HTTP outcomes and direct database/Redis invariants.
5. Add failure injection hooks at the relay publisher and Redis release boundaries that are test-only/configurable, not production bypasses.
6. Run targeted module tests, then `./mvnw.cmd test`, then Compose rebuild and live API/OpenAPI checks.
7. Record final test counts, known risks, and any deferred provider/cache-warming decisions in the plan Session Notes.

## Invariants

- The accepted reservation count never exceeds warmed stock.
- One order id maps to one Order and one set of order items.
- One successful payment maps to one sold-quantity increment and one ticket set.
- Every release is observable and idempotent.
- No test relies only on mocks for Redis Lua, RabbitMQ TTL/DLX, or PostgreSQL transaction/constraint behavior.

## Success Criteria

- All lifecycle, concurrency, duplicate, expiry, and recovery tests pass with real containers.
- `./mvnw.cmd test` passes across all five modules.
- `docker compose up -d --build` starts the complete local stack and the live API responds successfully.
- `/v3/api-docs` shows Bearer security and the new protected endpoints.
- No raw credentials are committed; no domain framework imports are introduced.

## Test Strategy

- Use unit tests for pure domain/application rules and full-container tests for cross-system behavior.
- Keep test data isolated by unique UUIDs/order ids; clean or recreate containers between suites when schema/state could leak.
- Use short configurable hold durations only in tests and assert persisted `expiresAt` rather than relying solely on wall-clock sleeps.

## Rollback Notes

If a full-stack test exposes a design defect, stop before modifying production code opportunistically. Update the relevant phase/architecture decision, rerun the affected lower-level test, then resume from the failed gate.

## Risks

- Three-container tests are slower and Docker-dependent; retain focused unit tests so failures remain diagnosable.
- Timing-based TTL tests can be flaky; use broker confirmation, persisted deadlines, polling with bounded timeouts, and a test-only short delay rather than exact sleep assertions.


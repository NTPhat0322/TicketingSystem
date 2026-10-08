# Phase 6: TTL/DLX expiry, idempotent release, and reconciliation

## Implementation Status

- [x] Versioned expiry message with per-message RabbitMQ TTL and DLX routing.
- [x] Conditional PostgreSQL expiry transition with `ORDER_EXPIRED` Outbox persistence.
- [x] Idempotent Redis release after the database transition commits.
- [x] Reconciliation scan for pending, stale enqueued, expired, and incomplete-release intents.
- [x] Payment/expiry race protection and terminal `COMPLETED` intent handling.
- [x] Unit, Redis, RabbitMQ, PostgreSQL, reactor, and Docker verification.

## P1 Stories Satisfied

- Unpaid holds expire and restore inventory.
- Stuck intents and lost messages are recoverable.

## Affected Areas

- `application/src/main/java/com/tienphat/application/order/`
- `domain/src/main/java/com/tienphat/domain/port/`
- `infrastructure/src/main/java/com/tienphat/infrastructure/messaging/`
- `infrastructure/src/main/java/com/tienphat/infrastructure/reconciliation/`
- `infrastructure/src/main/java/com/tienphat/infrastructure/redis/`
- Redis/Rabbit/PostgreSQL integration tests

## Requirements

1. Schedule an expiry message using the persisted `expiresAt` and per-message RabbitMQ expiration. The delay queue dead-letters to the expiry queue.
2. Implement `ExpireOrderUseCase` so it conditionally changes only `PENDING_PAYMENT` to `EXPIRED`, records `OutboxEvent(ORDER_EXPIRED)` in the same PostgreSQL transaction, commits that database transition, then calls idempotent Redis release.
3. If the Order row does not exist when the hold deadline passes, reconciliation must release the Redis reservation from the intent and mark the intent terminal; it must not create a fake Order.
4. Implement retry/reconciliation scans for:
   - due `PENDING` intents;
   - `ENQUEUED` intents whose Order is still absent after a grace period;
   - expired intents/holds without a successful release;
   - Orders marked `EXPIRED` whose Redis release is incomplete.
5. Use a bounded retry/grace policy and structured logs with `orderId`, `ticketTypeId`, attempt, and state. The policy must be configurable.

## Implementation Steps

1. [x] Add expiry message DTO, delay queue binding, publisher, and listener.
2. [x] Add an application service that performs the conditional DB transition in a transaction and invokes release only after commit. Make the operation safe to invoke repeatedly.
3. [x] Add Redis intent-state transitions for released/expired/completed cleanup.
4. [x] Add the scheduled reconciliation job. Protect each order with the same claim/lease mechanism used by the relay.
5. [x] Keep reconciliation as an internal scheduled diagnostic path; no unrestricted production endpoint is exposed.
6. [x] Add tests for payment/expiry races, missing Order, duplicate expiry messages, release failure, and eventual repair.

## Invariants

- `PENDING_PAYMENT → EXPIRED` is the only transition that authorizes hold release for normal expiry.
- `ORDER_EXPIRED` is recorded only when the conditional database transition succeeds; a replayed expiry message must not create another expiry event.
- `PAID` Orders are never expired and their sale-time stock is not returned by the expiry path.
- The same reservation cannot be released twice, even if DLX and reconciliation both process it.
- Redis release failure leaves an observable repairable state rather than silently losing the reservation.

## Success Criteria

- A real Rabbit TTL/DLX test delivers an expiry message after the configured delay.
- An unpaid Order becomes `EXPIRED` and restores stock/user allowance exactly once.
- Replaying expiry after the Order is already `PAID` leaves the Order and stock unchanged.
- A crash/stale-intent test is repaired by reconciliation without an Order duplicate or stock leak.
- Full reconciliation can be run repeatedly without changing a terminal reservation after the first successful repair.

## Test Strategy

- Real RabbitMQ container for delay/DLX behavior.
- Real PostgreSQL + Redis for conditional transition/release tests.
- Deterministic clock/configuration or short test hold durations; avoid sleeps longer than necessary.
- Concurrent expiry/payment test with locks/conditional updates.

## Rollback Notes

If delay/DLX behavior is unstable, disable expiry consumers and run the reconciliation path in a controlled mode; never delete the Redis release logic or manually increment stock without the idempotency marker.

## Risks

- RabbitMQ message expiration can be approximate under queue load; `expiresAt` must be rechecked by the worker.
- A database commit followed by Redis failure is an expected cross-store gap; reconciliation is part of correctness, not an optional monitoring feature.

## Verification Evidence

- Focused post-review verification: Redis state/reconciliation tests passed, 12 tests, 0 failures/errors.
- Full reactor `./mvnw.cmd -pl bootstrap -am '-Dsurefire.failIfNoSpecifiedTests=false' test`: 485 tests passed, 0 failures/errors/skips.
- Infrastructure suite included real PostgreSQL, Redis, and RabbitMQ containers: 73 tests passed.
- Real TTL/DLX integration: unpaid Order became `EXPIRED` and released stock once; replay remained idempotent. Payment-winning replay kept stock unchanged and eventually marked the intent `COMPLETED`.
- `docker compose up -d --build`: PostgreSQL, Redis, RabbitMQ, and app started successfully.
- Live `GET http://localhost:8080/v3/api-docs`: HTTP 200 after application readiness; app logs contain `Started Application` with no startup error.
- `git diff --check`: no whitespace errors; only Windows LF/CRLF conversion warnings were reported.

## Phase Error Report

1. The first guessed phase-document filename did not exist; the actual file was located as `phase-06-expiry-and-reconciliation.md` and no scope was changed.
2. `OrderExpiryPublisher` initially had a test-friendly constructor but no Spring-recognized primary constructor, causing `BeanCreationException: No default constructor found`; explicit `@Autowired` constructor wiring fixed it.
3. `ReservationReconciliationJob` exposed the same missing-constructor-wiring failure; explicit `@Autowired` wiring fixed it.
4. The payment-winning TTL test initially asserted `COMPLETED` immediately after the Order became `PAID`; Rabbit processing had not finished and the observed state was still `ORDER_CREATED`. The assertion now waits for eventual `COMPLETED`, while also verifying stock is never released and no extra `ORDER_EXPIRED` event is created.
5. Code review found that a late relay retry could move a `COMPLETED` intent back to `PENDING`. The Redis Lua reschedule guard now treats `COMPLETED` as terminal, with a Redis integration regression test.
6. The first live OpenAPI request after recreating the app raced startup and the connection closed unexpectedly; retrying after readiness returned HTTP 200.
7. Non-blocking warnings observed: Mockito/Byte Buddy dynamic-agent warnings, Hibernate `open-in-view` and missing-constraint schema warnings, Spring Data Redis repository-assignment messages, malformed-message rejection stack traces from negative tests, and expected PostgreSQL duplicate-key logs from concurrency/unique-constraint tests. The final reactor had no failures or errors.

## Review

`code-review` verdict: **APPROVED**. No actionable findings remain for TTL/DLX routing, transaction ordering, idempotent release, reconciliation claims, or terminal-state transitions.

## Next Gate

Phase 6 is complete. The next gate is explicit approval before Phase 7: local/test payment initiation and callback, ticket issuance, and Outbox publishing.

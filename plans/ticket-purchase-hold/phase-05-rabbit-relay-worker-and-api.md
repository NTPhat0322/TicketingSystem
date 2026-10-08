# Phase 5: RabbitMQ relay, idempotent Order worker, reservation API, and polling

## Implementation Status

- [x] Versioned order-create message with reservation snapshot.
- [x] Redis Hash + Sorted Set relay with claim lease, publisher confirms, and bounded backoff.
- [x] Manual-ack Rabbit listener and idempotent transactional Order worker.
- [x] Order/OrderItem/`ORDER_CREATED` Outbox persistence without changing `sold_quantity`.
- [x] Authenticated reservation endpoint returning `202 CREATING`.
- [x] Owner-checked polling endpoint and Bearer/OpenAPI metadata.
- [x] Unit, controller, real Redis/Rabbit/PostgreSQL integration, reactor, and Docker verification.

## P1 Stories Satisfied

- `202 Accepted` with `CREATING`.
- Retryable asynchronous Order creation.
- Idempotent Order worker.
- Client polling to `PENDING_PAYMENT`.

## Affected Areas

- `application/src/main/java/com/tienphat/application/order/`
- `application/src/main/java/com/tienphat/application/reservation/`
- `infrastructure/src/main/java/com/tienphat/infrastructure/messaging/`
- `presentation/src/main/java/com/tienphat/presentation/order/`
- `presentation/src/main/java/com/tienphat/presentation/config/UseCaseConfig.java`
- OpenAPI/security configuration and tests

## Requirements

1. Define a versioned order-create message containing `orderId`, user/event/ticket identifiers, quantity, unit-price snapshot, reserved/expires timestamps, and a message/event id.
2. Implement the scheduled relay over Redis Hash + Sorted Set. It must claim work briefly, publish persistent messages with publisher confirms, mark the intent as enqueued, and reschedule failures with bounded backoff metadata.
3. Implement a Rabbit listener that acknowledges only after the Order transaction commits. A duplicate `orderId` must be treated as a successful no-op, not a second insert.
4. Implement the Order creation use case/worker transaction: build Order with the preassigned ID, add one OrderItem, persist Order/OrderItem, and record `ORDER_CREATED` OutboxEvent. It must not touch `sold_quantity`.
5. Add `POST /api/v1/orders` for authenticated reservation and `GET /api/v1/orders/{orderId}` for ownership-checked polling.
6. Map business failures to stable API responses: sold out/user limit as conflict-style errors, Redis outage as fail-closed service-unavailable behavior, `CREATING` as 202, and missing/foreign orders as 404/403 according to the existing error conventions.
7. Register all new application services in `UseCaseConfig` and add Bearer security/OpenAPI metadata for the two protected Order endpoints.

## Implementation Steps

1. Add message DTO/serializer and exchange/routing-key constants in infrastructure; keep domain models out of the wire format.
2. Implement the relay scheduler and publisher-confirm callback. Ensure `ZREM` happens after confirmation, never before.
3. Implement the listener with manual acknowledgment and transaction boundary. Configure retry/DLQ behavior for malformed/permanently invalid messages separately from transient DB errors.
4. Implement `CreateOrderFromReservationUseCase` using the captured price/expiry snapshot.
5. Implement the reservation and polling controllers/DTO mappers.
6. Add MockMvc tests for auth, response codes, `CREATING`, ownership, and error mapping.
7. Add a full-context test proving Redis reservation → Rabbit message → PostgreSQL Order with real containers.

## Invariants

- The client never sees `PENDING_PAYMENT` before the Order transaction commits.
- One `orderId` produces at most one Order and one set of OrderItems.
- A relay crash can cause duplicate messages but cannot lose a still-pending intent.
- Message payload price/expiry is the reservation snapshot, not a current mutable TicketType value.
- Rabbit listener acknowledgment happens after durable DB success.

## Success Criteria

- `POST /api/v1/orders` returns 202 and a stable `orderId` after a real Redis reservation.
- Polling returns `CREATING` before worker processing and `PENDING_PAYMENT` after the worker commits.
- Replaying the same Rabbit message produces exactly one Order and one `ORDER_CREATED` event.
- Publishing failure leaves the intent in the Sorted Set with a later retry time.
- Unauthorized and cross-user polling attempts are rejected.

## Test Strategy

- Application unit tests for worker idempotency and snapshot mapping.
- MockMvc/controller tests for 202, CREATING, ownership, and error contracts.
- Real Redis/Rabbit/PostgreSQL integration test for the happy path and duplicate delivery.

## Rollback Notes

If RabbitMQ is unavailable in a deployment, leave reservations fail/retry according to intent state; do not revert to synchronous PostgreSQL reservation silently. The endpoint can be disabled while existing Event/Auth APIs continue running.

## Risks

- A publisher confirm proves broker acceptance, not worker completion; polling and intent reconciliation must remain independent.
- Multiple relay instances need a claim lease; idempotent worker correctness must not depend on the lease being perfect.

## Verification Evidence

- Full reactor `./mvnw.cmd -pl bootstrap -am '-Dsurefire.failIfNoSpecifiedTests=false' test`: 463 tests passed, 0 failures/errors/skips.
- Infrastructure `./mvnw.cmd -pl infrastructure -am '-Dsurefire.failIfNoSpecifiedTests=false' test`: 65 tests passed.
- Real Redis/Rabbit/PostgreSQL order flow and topology tests: 12 tests passed.
- `docker compose up -d --build`: all four services started healthy.
- Live `GET http://localhost:8080/v3/api-docs`: HTTP 200; `POST /api/v1/orders` and `GET /api/v1/orders/{orderId}` both declare `bearerAuth`.
- `git diff --check`: passed; only CRLF conversion warnings appeared.

## Phase Error Report

1. The first Maven dependency-tree invocation omitted upstream reactor modules; `-am` fixed the command.
2. A parallel Maven test attempt caused shared-target class-loading races; sequential execution passed.
3. Initial test configuration lacked Rabbit queue properties, and the relay/listener conditions allowed infrastructure-only contexts to instantiate incomplete messaging beans; test properties and conditional wiring were corrected.
4. An early polling assertion inspected an empty result before Awaitility had observed it; the assertion now waits for presence.
5. The relay scheduler interfered with an adapter-only TTL test; relay is disabled there and enabled for the real order-flow test.
6. The first live OpenAPI request raced application startup and saw a closed connection; retrying after readiness returned 200.
7. Non-blocking warnings observed: Hibernate missing-constraint/open-in-view warnings, Mockito/Byte Buddy agent warnings, and expected malformed-message rejection logs. No unresolved test or runtime error remains.

## Review

`code-review` verdict: **APPROVED**. No actionable findings remain. TTL/DLX expiry, stock release, and reconciliation are intentionally Phase 6 work.

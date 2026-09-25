# Phase 5: RabbitMQ relay, idempotent Order worker, reservation API, and polling

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

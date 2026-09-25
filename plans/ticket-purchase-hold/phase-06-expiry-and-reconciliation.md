# Phase 6: TTL/DLX expiry, idempotent release, and reconciliation

## P1 Stories Satisfied

- Unpaid holds expire and restore inventory.
- Stuck intents and lost messages are recoverable.

## Affected Areas

- `application/src/main/java/com/tienphat/application/order/`
- `application/src/main/java/com/tienphat/application/reconciliation/`
- `infrastructure/src/main/java/com/tienphat/infrastructure/messaging/`
- `infrastructure/src/main/java/com/tienphat/infrastructure/reconciliation/`
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

1. Add expiry message DTO, delay queue binding, publisher, and listener.
2. Add an application service that performs the conditional DB transition in a transaction and invokes release only after commit. Make the operation safe to invoke repeatedly.
3. Add Redis intent-state transitions for released/expired/completed cleanup.
4. Add the scheduled reconciliation job. Protect each order with the same claim/lease mechanism used by the relay.
5. Add a manual/diagnostic reconciliation entry point for local verification if useful, without exposing an unrestricted production endpoint.
6. Add tests for payment/expiry races, missing Order, duplicate expiry messages, release failure, and eventual repair.

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

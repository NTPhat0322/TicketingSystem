# Phase 4: Buyer cancels a pending order

## Affected Areas

- `application/src/main/java/com/tienphat/application/order/`
- `presentation/src/main/java/com/tienphat/presentation/order/OrderController.java`
- `presentation/src/main/java/com/tienphat/presentation/config/UseCaseConfig.java`

## Implementation Steps

1. [x] `CancelOrderCommand(UUID orderId, AuthorizationContext actor)`.
2. [x] `CancelOrderTransaction` (separate bean so `@Transactional` is proxied, like `ExpireOrderTransaction`):
   ```text
   @Transactional execute(command) → Optional<CancelOrderTransactionResult>
     order = findByIdForUpdate(orderId)          absent → Optional.empty()
     actor.requireCanAccess(order.userId)
     PENDING_PAYMENT → order.cancel(); save
     CANCELLED       → no write (idempotent retry)
     PAID/EXPIRED/FAILED → InvalidOrderStateException (409)
     payment = paymentRepository.findByOrderId (no lock)
     return (OrderStatusResult.fromOrder(order, payment), ReleaseReservation(order))
   ```
   `CancelOrderTransactionResult(OrderStatusResult order, ExpireOrderResult.ReleaseReservation release)`.
3. [x] `CancelOrderUseCase implements UseCase<CancelOrderCommand, OrderStatusResult>` (not transactional):
   - validate command, `requireAuthenticated`;
   - run the transaction; on `Optional.empty()` read `ReservationIntentStatusPort`:
     - Redis read fails → `ReservationServiceUnavailableException` (503);
     - no intent → `OrderNotFoundException` (404);
     - intent exists → `requireCanAccess(intent.userId)`, then `InvalidOrderStateException` (409):
       "still being created" or "already expired" depending on intent state;
   - after commit: `stockCachePort.release(orderId, ticketTypeId, userId, quantity)`; exceptions propagate.
4. [x] `ExpireOrderTransaction`: `CANCELLED, FAILED` → `OTHER_TERMINAL` **with** `releaseReservation(order)`
   (safety net for a failed cancel/payment-failure release; release is idempotent).
5. [x] `POST /api/v1/orders/{orderId}/cancel` → 200 `OrderResponse`.
6. [x] Beans: `CancelOrderTransaction`, `UseCase<CancelOrderCommand, OrderStatusResult> cancelOrderUseCase`.

## Status Codes

| Case | Code |
|------|------|
| PENDING_PAYMENT, owner or ADMIN | 200 `CANCELLED` |
| already CANCELLED (retry) | 200 `CANCELLED`, release re-run (no double restore) |
| PAID / EXPIRED / FAILED | 409 |
| Redis-only CREATING or expired intent | 409 |
| other user | 403 |
| unknown order | 404 |
| Redis unavailable (intent lookup or release) | 503 / propagated failure, DB already CANCELLED |

## Tests

- `CancelOrderTransactionTest`: pending → cancel + save + release coords; cancelled → no save + coords;
  each other terminal status → 409 and no save; foreign user → Forbidden before any write; missing → empty.
- `CancelOrderUseCaseTest`: release called once after transaction with exact coords; release failure
  propagates; missing order → 404 / 409 creating / 409 expired / 403 foreign intent / 503 Redis error;
  release not called on any rejection.
- `ExpireOrderTransactionTest`: CANCELLED and FAILED return `OTHER_TERMINAL` with release coordinates.
- `ExpireOrderUseCaseTest`: `OTHER_TERMINAL` with release → `markExpired` + `release`.
- `OrderControllerTest`: 200, 401, 403, 404, 409 mapping.

## Exit Criteria

`mvn -pl application,presentation -am test` green.

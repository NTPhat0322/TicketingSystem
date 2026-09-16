# Phase 7: Payment Aggregate

## Requirements
Model the `Payment` aggregate root with guarded `markSuccess()`/`markFailed()` transitions, reusing `DuplicatePaymentException` (defined in Phase 5) as the webhook-replay idempotency guard.

## Steps
1. Implement `PaymentProvider` enum (`VNPAY`, `MOMO`, `STRIPE`) and `PaymentStatus` enum (`PENDING`, `SUCCESS`, `FAILED`) with transition table.
2. Implement `Payment` entity mirroring the `payments` table: `id`, `orderId`, `provider`, `amount` (`Money`), `status`, `transactionRef`, `paidAt`, `createdAt`.
3. Implement the static factory `Payment.initiate(...)`.
4. Implement guarded methods `markSuccess()` and `markFailed()`.
5. Implement `InvalidPaymentDataException` and `InvalidPaymentStateException` (reuse `DuplicatePaymentException` from Phase 5 — do not redefine it).
6. Implement `PaymentRepository` port (`save`, `findById`, `findByOrderId`, `findByTransactionRef`).
7. Write `PaymentTest` and `PaymentStatusTest`; run `mvn test -pl domain` and confirm all tests pass.

## Files to Create
- `domain/src/main/java/com/tienphat/domain/model/PaymentProvider.java`
- `domain/src/main/java/com/tienphat/domain/model/PaymentStatus.java`
- `domain/src/main/java/com/tienphat/domain/model/Payment.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidPaymentDataException.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidPaymentStateException.java`
- `domain/src/main/java/com/tienphat/domain/repository/PaymentRepository.java`
- `domain/src/test/java/com/tienphat/domain/model/PaymentTest.java`
- `domain/src/test/java/com/tienphat/domain/model/PaymentStatusTest.java`

## Design Detail

**`PaymentStatus` transition table**:
| From | Allowed To |
|---|---|
| `PENDING` | `SUCCESS`, `FAILED` |
| `SUCCESS` | *(none — terminal)* |
| `FAILED` | *(none — terminal; no retry-from-FAILED modeled in this plan — see Risks)* |

**`Payment`** (`@Getter`, `@Builder(access = AccessLevel.PRIVATE)`, `@EqualsAndHashCode(of = "id")`):
- `static Payment initiate(UUID orderId, PaymentProvider provider, Money amount, String transactionRef)`: throws `InvalidPaymentDataException` if `amount.isZero()` or `transactionRef` is blank; `id = UUID.randomUUID()`; `status = PENDING`; `createdAt = Instant.now()`; `paidAt = null`.
- `void markSuccess()`: if `status == SUCCESS`, throws `DuplicatePaymentException` (**reused from Phase 5** — this is the webhook-replay idempotency guard called out in design doc §2.3, complementary to the DB-level unique constraint on `transaction_ref`); else if `status == FAILED`, throws `InvalidPaymentStateException`; else `status = SUCCESS`, `paidAt = Instant.now()`.
- `void markFailed()`: `PENDING → FAILED`; else throws `InvalidPaymentStateException` (covers a `SUCCESS` payment being incorrectly marked failed).

**`PaymentRepository`**: `Payment save(Payment payment)`, `Optional<Payment> findById(UUID id)`, `Optional<Payment> findByOrderId(UUID orderId)`, `Optional<Payment> findByTransactionRef(String transactionRef)`.

## Unit Tests
**`PaymentTest`**:
- `initiate()` succeeds, `status == PENDING`, `paidAt == null`.
- `initiate()` throws `InvalidPaymentDataException` when `amount` is zero.
- `initiate()` throws `InvalidPaymentDataException` when `transactionRef` is blank.
- `markSuccess()` succeeds from `PENDING`, sets `paidAt`.
- `markSuccess()` throws `DuplicatePaymentException` when called a second time (simulates webhook replay).
- `markSuccess()` throws `InvalidPaymentStateException` when called on a `FAILED` payment.
- `markFailed()` succeeds from `PENDING`.
- `markFailed()` throws `InvalidPaymentStateException` when called on a `SUCCESS` payment.

**`PaymentStatusTest`**: exhaustive transition-table coverage.

## Success Criteria (Pass/Fail)
- `mvn test -pl domain` passes, including all `PaymentTest`/`PaymentStatusTest` cases.
- `Payment` has no public setter.
- No duplicate `DuplicatePaymentException` class is created in this phase — verified by import, it must resolve to the Phase 5 file under `exception/`.

## Risks
- LOW: `FAILED` is modeled as terminal (no `FAILED → PENDING` retry path), since design doc §4 doesn't specify a retry flow for failed payments. If the application layer needs "retry payment," it will need either a new `Payment` row (new `transactionRef`) or a future domain change to reopen this transition — flagged as an assumption, not a blocker.

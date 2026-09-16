# Phase 5: Order + OrderItem Aggregate

## Requirements
Model the `Order` aggregate root (with child `OrderItem`) including application-generated UUIDv7 ids, guarded `pay()`/`expire()`/`cancel()` transitions, and total-amount recalculation — the aggregate with the most guarded invariants in this plan.

## Steps
1. Implement a package-private `UuidV7Generator` (no external dependency — hand-rolled, unix-ms-based, version/variant nibbles set correctly).
2. Implement `OrderStatus` enum (`PENDING_PAYMENT`, `PAID`, `EXPIRED`, `CANCELLED`, `FAILED`) with transition table.
3. Implement `OrderItem` (child entity, own UUID, constructed only via `Order.addItem(...)`, no public factory of its own).
4. Implement `Order` entity mirroring the `orders` table, with `List<OrderItem> items` and a `recalculateTotal()` helper.
5. Implement guarded methods `addItem(...)`, `pay()`, `expire()`, `cancel()`, `fail()`.
6. Implement the three `Order`-specific exceptions (`InvalidOrderStateException`, `InvalidOrderItemException`, `DuplicatePaymentException`).
7. Implement `OrderRepository` port (no separate `OrderItemRepository`).
8. Write `OrderTest`, `OrderStatusTest`, and `UuidV7GeneratorTest`; run `mvn test -pl domain` and confirm all tests pass.

## Files to Create
- `domain/src/main/java/com/tienphat/domain/model/UuidV7Generator.java`
- `domain/src/main/java/com/tienphat/domain/model/OrderStatus.java`
- `domain/src/main/java/com/tienphat/domain/model/OrderItem.java`
- `domain/src/main/java/com/tienphat/domain/model/Order.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidOrderStateException.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidOrderItemException.java`
- `domain/src/main/java/com/tienphat/domain/exception/DuplicatePaymentException.java`
- `domain/src/main/java/com/tienphat/domain/repository/OrderRepository.java`
- `domain/src/test/java/com/tienphat/domain/model/OrderTest.java`
- `domain/src/test/java/com/tienphat/domain/model/OrderStatusTest.java`
- `domain/src/test/java/com/tienphat/domain/model/UuidV7GeneratorTest.java`

## Design Detail

**`UuidV7Generator`** (package-private, no public API outside `model`): `static UUID generate()` — 48-bit unix-ms timestamp in the high bits, version nibble set to `7`, variant bits set per RFC 9562, remaining bits filled from `SecureRandom`. Used only inside `Order.create()`; this keeps UUIDv7 generation a domain-owned decision per design doc §2.1 ("Order.id sinh ở application... gợi ý dùng UUIDv7") without adding a new Maven dependency.

**`OrderStatus` transition table**:
| From | Allowed To |
|---|---|
| `PENDING_PAYMENT` | `PAID`, `EXPIRED`, `CANCELLED`, `FAILED` |
| `PAID` | *(none — terminal)* |
| `EXPIRED` | *(none — terminal)* |
| `CANCELLED` | *(none — terminal)* |
| `FAILED` | *(none — terminal)* |

**`OrderItem`** (`@Getter`, `@Builder(access = AccessLevel.PRIVATE)`, `@EqualsAndHashCode(of = "id")`): fields `id`, `orderId`, `ticketTypeId`, `quantity`, `unitPrice` (`Money`), `subtotal` (`Money`). Constructed only by a package-private static factory `OrderItem.of(UUID orderId, UUID ticketTypeId, int quantity, Money unitPrice)` called from `Order.addItem(...)`: throws `InvalidOrderItemException` if `quantity <= 0`; `subtotal = unitPrice.multiply(quantity)`.

**`Order`** (`@Getter`, `@Builder(access = AccessLevel.PRIVATE)`, `@EqualsAndHashCode(of = "id")`):
- Fields: `UUID id` (final, from `UuidV7Generator`), `String orderCode`, `UUID userId`, `UUID eventId`, `OrderStatus status`, `Money totalAmount`, `List<OrderItem> items` (private mutable backing list), `Instant reservedAt`, `Instant expiresAt`, `Instant paidAt` (nullable), `Instant createdAt`, `Instant updatedAt`.
- `static Order create(String orderCode, UUID userId, UUID eventId, int holdDurationSec)`: generates `id` via `UuidV7Generator.generate()`; `status = PENDING_PAYMENT`; `totalAmount = Money.zero()`; `items = new ArrayList<>()`; `reservedAt = Instant.now()`; `expiresAt = reservedAt.plusSeconds(holdDurationSec)`; `createdAt = updatedAt = reservedAt`.
- `void addItem(UUID ticketTypeId, int quantity, Money unitPrice)`: throws `InvalidOrderStateException` if `status != PENDING_PAYMENT`; builds an `OrderItem` via `OrderItem.of(...)` (throws `InvalidOrderItemException` on bad quantity), appends to `items`, calls `recalculateTotal()`, bumps `updatedAt`.
- `private void recalculateTotal()`: `totalAmount = items.stream().map(OrderItem::getSubtotal).reduce(Money.zero(), Money::add)`.
- `void pay()`: if `status == PAID`, throws `DuplicatePaymentException` (domain-level idempotency guard — complementary to, not a replacement for, the DB-level unique constraint on `payments.transaction_ref`); else if `!status.canTransitionTo(PAID)`, throws `InvalidOrderStateException`; else `status = PAID`, `paidAt = Instant.now()`, bump `updatedAt`.
- `void expire()`: `PENDING_PAYMENT → EXPIRED`; else throws `InvalidOrderStateException` (including from `PAID` — the future `ExpireOrderUseCase` must check `status == PAID` first and skip calling `expire()` rather than rely on a silent no-op here, per design doc §2.2's "nếu đã PAID thì bỏ qua" being a worker-level check, not a domain-level one).
- `void cancel()`: `PENDING_PAYMENT → CANCELLED`; else throws `InvalidOrderStateException`.
- `void fail()`: `PENDING_PAYMENT → FAILED`; else throws `InvalidOrderStateException`. Symmetric to `expire()`/`cancel()` — no new exception class needed. Without this method `FAILED` would be a dead enum value: `OrderStatus.canTransitionTo(FAILED)` reports it as legal (and the DB schema in design doc §4 lists it as a real `orders.status` value) but no guarded mutator ever reached it, so no future application-layer use case could legally fail an order. Added per plan-review finding (2026-09-15, ACCEPTED).
- `List<OrderItem> getItems()`: returns `Collections.unmodifiableList(new ArrayList<>(items))` — defensive copy, never the live list.

**`OrderRepository`**: `Order save(Order order)` (cascades `OrderItem`s), `Optional<Order> findById(UUID id)`, `Optional<Order> findByOrderCode(String orderCode)`.

## Unit Tests
**`OrderTest`**:
- `create()` succeeds, `status == PENDING_PAYMENT`, `totalAmount` is zero, `expiresAt == reservedAt + holdDurationSec`.
- `addItem()` succeeds and `recalculateTotal()` reflects the new subtotal.
- `addItem()` throws `InvalidOrderItemException` when `quantity <= 0`.
- `addItem()` throws `InvalidOrderStateException` when called after `pay()` has already transitioned the order out of `PENDING_PAYMENT`.
- `pay()` succeeds from `PENDING_PAYMENT`, sets `paidAt`.
- `pay()` throws `DuplicatePaymentException` when called a second time on an already-`PAID` order.
- `pay()` throws `InvalidOrderStateException` when called on a `CANCELLED` order.
- `expire()` succeeds from `PENDING_PAYMENT`.
- `expire()` throws `InvalidOrderStateException` from `PAID`.
- `cancel()` succeeds from `PENDING_PAYMENT`.
- `cancel()` throws `InvalidOrderStateException` from `EXPIRED`.
- `fail()` succeeds from `PENDING_PAYMENT`.
- `fail()` throws `InvalidOrderStateException` from `PAID`.
- `getItems()` returns a list that throws `UnsupportedOperationException` on `add(...)`.

**`OrderStatusTest`**: exhaustive transition-table coverage.

**`UuidV7GeneratorTest`**:
- Generated value is a valid `UUID` with version nibble `7` and RFC-variant bits set.
- Two successive calls produce ids whose extracted timestamp portion is non-decreasing (sortability property, not strict monotonic uniqueness within the same millisecond).

## Success Criteria (Pass/Fail)
- `mvn test -pl domain` passes, including all `OrderTest`/`OrderStatusTest`/`UuidV7GeneratorTest` cases.
- `Order` has no public setter; `items` is never returned as the live backing list.
- No separate `OrderItemRepository` exists anywhere in `repository/`.

## Risks
- MEDIUM (carried from `plan.md`): `expire()` is strict and will throw if the order already transitioned to `PAID` — this is intentional (keeps the domain method simple/pure), but means the future `ExpireOrderUseCase` implementer must explicitly handle/ignore `InvalidOrderStateException` in that specific race scenario rather than letting it propagate as an error.
- MEDIUM (carried from `plan.md`): UUIDv7 hand-rolled with no external library — mitigated by the dedicated `UuidV7GeneratorTest` checking version/variant bits and ordering; revisit if a vetted library dependency is later approved.

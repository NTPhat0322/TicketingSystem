# Phase 4: TicketType Aggregate

## Requirements
Model `TicketType` as its own aggregate root (own repository, not nested under `Event`) with `confirmSale()` as the **single** Postgres-side stock mutator, plus the `StockCachePort` tech-agnostic port that the future infrastructure layer backs with the Redis+Lua script.

Design doc §2.4 settled the strategy question this phase used to carry as a HIGH risk: every event reserves through Redis, so the domain has **no** reserve-time write to Postgres at all. Reserve/release live entirely behind `StockCachePort`; `soldQuantity` moves only at payment confirmation.

## Steps
1. Implement `TicketTypeStatus` enum (`ACTIVE`, `SOLD_OUT`, `CLOSED`) with transition table.
2. Implement `TicketType` entity mirroring the `ticket_types` table: `id`, `eventId`, `name`, `price` (`Money`), `totalQuantity`, `soldQuantity`, `maxPerUser`, `holdDurationSec`, `version`, `status`, `createdAt`, `updatedAt`.
3. Implement the static factory `TicketType.create(...)` with parameter validation.
4. Implement `confirmSale(int qty)` — the only method that mutates `soldQuantity` — plus `releaseSale(int qty)` for the payment-refund/compensation decrement and `close()` for the organizer's stop-selling transition. **No `reserve()` method**: reserve-time inventory is Redis's job (design doc §2.4).
5. Implement the four `TicketType`-specific exceptions.
6. Implement `TicketTypeRepository` port (`save`, `findById`, `findAllByEventId`) and `StockCachePort` (Redis-abstraction port, no Redis types in the signature, carrying both the stock and the `max_per_user` counter semantics).
7. Write `TicketTypeTest` (factory + both guarded methods, including the SOLD_OUT transition) and `TicketTypeStatusTest`.
8. Run `mvn test -pl domain` and confirm all tests pass.

## Files to Create
- `domain/src/main/java/com/tienphat/domain/model/TicketType.java`
- `domain/src/main/java/com/tienphat/domain/model/TicketTypeStatus.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidTicketTypeDataException.java`
- `domain/src/main/java/com/tienphat/domain/exception/InsufficientStockException.java`
- `domain/src/main/java/com/tienphat/domain/exception/TicketTypeNotAvailableException.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidReservationQuantityException.java`
- `domain/src/main/java/com/tienphat/domain/repository/TicketTypeRepository.java`
- `domain/src/main/java/com/tienphat/domain/port/StockCachePort.java`
- `domain/src/main/java/com/tienphat/domain/port/ReservationResult.java`
- `domain/src/test/java/com/tienphat/domain/model/TicketTypeTest.java`
- `domain/src/test/java/com/tienphat/domain/model/TicketTypeStatusTest.java`

## Design Detail

**`TicketTypeStatus` transition table**:
| From | Allowed To |
|---|---|
| `ACTIVE` | `SOLD_OUT` (via `confirmSale()` reaching capacity), `CLOSED` (via `close()`) |
| `SOLD_OUT` | `ACTIVE` (capacity reopened by `releaseSale()` — refund/chargeback, **not** a restock; no mutator in this phase raises `totalQuantity`), `CLOSED` (via `close()`) |
| `CLOSED` | *(none — terminal)* |

**`TicketType`** (`@Getter`, `@Builder(access = AccessLevel.PRIVATE)`, `@EqualsAndHashCode(of = "id")`):
- `static TicketType create(UUID id, UUID eventId, String name, Money price, int totalQuantity, int maxPerUser, int holdDurationSec)`: throws `InvalidTicketTypeDataException` if `totalQuantity <= 0`, `maxPerUser <= 0`, or `holdDurationSec <= 0`; `soldQuantity = 0`, `version = 0`, `status = ACTIVE`, `createdAt = updatedAt = Instant.now()`.
- `void confirmSale(int qty)` — **the only mutator of `soldQuantity` in the entire system** (design doc §2.2/§2.4): throws `InvalidReservationQuantityException` if `qty <= 0`; throws `TicketTypeNotAvailableException` if `status == CLOSED`; throws `InsufficientStockException` if `(totalQuantity - soldQuantity) < qty`; else `soldQuantity += qty`; if `soldQuantity == totalQuantity`, transition `status` to `SOLD_OUT`; bump `updatedAt`. **Intended call site**: `ConfirmPaymentUseCase` only, on payment SUCCESS.
  - **Guard ordering matters, and `status == CLOSED` is deliberate — but not for the reason it might look like.** With `create()`/`confirmSale()`/`releaseSale()` as the only mutators, the entity holds the invariant `status == SOLD_OUT` ⟺ `soldQuantity == totalQuantity` (`confirmSale` only flips to `SOLD_OUT` at exact capacity; `releaseSale` flips straight back the moment capacity reopens). So a `SOLD_OUT` type has zero remaining capacity and is rejected by the `InsufficientStockException` check regardless of which guard is used. `status == CLOSED` vs `status != ACTIVE` does **not** change *whether* the call is rejected — only *which exception surfaces*.
  - That difference is the whole point. A payment webhook arriving against a `SOLD_OUT` type means Redis handed out a reservation the Postgres counter cannot cover — Redis and Postgres have diverged, which is exactly what design doc §2.2's end-of-sale reconciliation alerts on. `InsufficientStockException` says that. A `status != ACTIVE` guard would run first and mask it with a generic "type not available", demoting a data-integrity signal to a routine rejection nobody investigates.
  - The capacity check is therefore defensive, not primary: Redis already guaranteed the quantity at reserve time, so in a healthy system it never fires. If it does fire, that is the signal.
- `void releaseSale(int qty)`: throws `InvalidReservationQuantityException` if `qty <= 0` or `soldQuantity - qty < 0`; else `soldQuantity -= qty`; if `status == SOLD_OUT` and `soldQuantity < totalQuantity`, transition back to `ACTIVE`; bump `updatedAt`. **Intended call site**: refund/chargeback compensation only — **not** the hold-expiry path (see the compensation table below).
- `void close()`: `ACTIVE`/`SOLD_OUT → CLOSED`; throws `TicketTypeNotAvailableException` if already `CLOSED` (reusing that exception rather than adding an `InvalidTicketTypeStateException` — it reads correctly as "this type is no longer available to operate on", and `TicketType` has no other state-transition method needing a distinct type). Bumps `updatedAt`. **Intended call site**: an organizer stopping sales for this tier. Added per plan-review finding (2026-09-15, ACCEPTED): without it `CLOSED` is a dead enum value — the transition table declares `ACTIVE → CLOSED` and `SOLD_OUT → CLOSED`, design doc §4 lists `CLOSED` as a real `ticket_types.status` value, and `confirmSale()`'s guard branches on it, yet no mutator could ever produce it, making the `confirmSale()`-on-`CLOSED` test below unwritable without a setter or reflection (both forbidden by plan.md's conventions).

**Compensation paths — which method returns what.** The reviewer flagged the Postgres-vs-Redis split as a silent-corruption trap; this table is the normative answer, and each method's doc comment points here rather than restating it:

| Flow | `TicketType.releaseSale()` (Postgres `soldQuantity`) | `StockCachePort.release()` (Redis stock + user allowance) |
|---|---|---|
| Hold expires (delay queue) | **No** — `soldQuantity` was never incremented for a mere hold (design doc §2.2) | **Yes** |
| User cancels before paying | **No** — same reason | **Yes** |
| Refund / chargeback after `PAID` | **Yes** — this is the only flow that decrements it | **Yes**, if the sale window is still open — see policy note below |

**Policy: a refund restores the user's `max_per_user` allowance.** `max_per_user` caps how many tickets one person *holds at a time*, and a refunded buyer holds none; burning their allowance permanently would punish a legitimate refund. This widens `StockCachePort.release(...)`'s documented callers beyond hold-expiry/cancel to include the refund flow. Not an abuse vector: the released stock goes back into the shared Redis pool for everyone, not reserved for the refunder, so cycling buy→refund gains nothing. Recorded here and in design doc §5 because the reviewer correctly noted it would otherwise be decided by omission (2026-09-15, ACCEPTED).

**`TicketTypeRepository`**: `TicketType save(TicketType ticketType)`, `Optional<TicketType> findById(UUID id)`, `List<TicketType> findAllByEventId(UUID eventId)`.

**`StockCachePort`** (tech-agnostic, implemented later by a Redis+Lua adapter in `infrastructure`). Since §2.4 removed the Postgres reserve path, this port is now the *only* reservation mechanism — its contract must cover the `max_per_user` limit too, because at reserve time Postgres has no order row to count against (§2.1):
- `ReservationResult tryReserve(UUID ticketTypeId, UUID userId, int quantity, int maxPerUser)` — atomic, single round trip: check per-user limit, check stock, decrement both, or change nothing. Takes `userId`/`maxPerUser` because the limit check must be inside the same atomic step as the stock check; splitting them reintroduces the race.
- `ReservationResult` is a small domain enum (`SUCCESS`, `OUT_OF_STOCK`, `USER_LIMIT_EXCEEDED`) rather than `boolean` — the two failure modes need different messages to the user, and a `boolean` would force the adapter to throw for one of them.
- `void release(UUID ticketTypeId, UUID userId, int quantity)` — INCR stock back **and** decrement the user's allowance counter. Both must move together or the user silently loses their remaining allowance. Callers: hold expiry, user cancel, and refund/chargeback — see the compensation table above.
- `int getAvailableStock(UUID ticketTypeId)`.
- `void warmUp(UUID ticketTypeId, int totalQuantity)` — cache-warming entry point (design doc §1, stage 1). Mandatory before sale, not optional: an unwarmed key reads as zero stock, which is indistinguishable from sold out.

`ReservationResult` lives at `domain/src/main/java/com/tienphat/domain/port/ReservationResult.java`, next to the port that returns it.

## Unit Tests
**`TicketTypeTest`**:
- `create()` succeeds with valid params, `status == ACTIVE`, `soldQuantity == 0`.
- `create()` throws `InvalidTicketTypeDataException` when `totalQuantity <= 0`.
- `confirmSale()` succeeds and increments `soldQuantity`.
- `confirmSale()` throws `InsufficientStockException` when requested qty exceeds remaining capacity.
- `confirmSale()` throws `TicketTypeNotAvailableException` when `status == CLOSED` — precondition reached by calling `close()` first, which is what makes this test writable at all.
- `close()` succeeds from `ACTIVE`; succeeds from `SOLD_OUT`; throws `TicketTypeNotAvailableException` when already `CLOSED`.
- `confirmSale()` throws `InvalidReservationQuantityException` when `qty <= 0`.
- `confirmSale()` transitions `status` to `SOLD_OUT` when `soldQuantity` reaches `totalQuantity`.
- `confirmSale()` on an already-`SOLD_OUT` type throws `InsufficientStockException`, **not** `TicketTypeNotAvailableException` — asserted on the exception type specifically, since both guards reject the call and only the exception distinguishes them. This pins the guard ordering: "tidying" `status == CLOSED` into `status != ACTIVE` flips this exception and silently masks the Redis/Postgres divergence signal, and this test is the only thing that catches that edit.
- Invariant test: after `confirmSale()` drives `soldQuantity` to `totalQuantity`, `status == SOLD_OUT`; after a subsequent `releaseSale(1)`, `status == ACTIVE` again. Pins `SOLD_OUT` ⟺ at-capacity, which is what makes the guard-ordering reasoning above valid. (Note: a `SOLD_OUT`-with-spare-capacity state is **not** reachable through the public API and therefore is not tested — it would become reachable only if a future `restock()`/`increaseQuantity()` mutator is added, at which point the `confirmSale` guard needs re-examining.)
- `releaseSale()` succeeds and decrements `soldQuantity`, flips `SOLD_OUT → ACTIVE` when capacity reopens.
- `releaseSale()` throws `InvalidReservationQuantityException` when it would drive `soldQuantity` negative.
- Reachability sweep: every `TicketTypeStatus` value is produced by at least one public method — `ACTIVE` by `create()`, `SOLD_OUT` by `confirmSale()` at capacity, `CLOSED` by `close()`. Asserted as a concrete test so a future status added to the enum without a matching mutator fails immediately, rather than surfacing as a guard nobody can trigger.
- No method named `reserve` exists on `TicketType` — asserted structurally by the compiler (nothing calls it) and by the Success Criteria grep below.

**`TicketTypeStatusTest`**: exhaustive transition-table coverage (all positive pairs `true`, all other pairs `false`), same pattern as `EventStatusTest`.

## Success Criteria (Pass/Fail)
- `mvn test -pl domain` passes, including all `TicketTypeTest`/`TicketTypeStatusTest` cases.
- `TicketType` has no public setter; `soldQuantity`/`status`/`version` only change via the guarded methods above.
- `grep -rn "reserve" domain/src/main/java/com/tienphat/domain/model/TicketType.java` returns zero matches — the reserve-time write path is gone from the entity, per design doc §2.4.
- `StockCachePort` contains zero references to any Redis/Jedis/Lettuce type in its method signatures — verified by inspection (no such dependency exists on the `domain` classpath to import in the first place, so this is structurally enforced by Phase 1's dependency scope).

## Risks
- MEDIUM: `confirmSale()`/`releaseSale()` are read-modify-write on `soldQuantity` with no domain-level atomicity. Two payment webhooks confirming the last tickets concurrently can lose an update, because `version` is explicitly no longer an inventory lock (design doc §2.4). Blast radius is bounded: Redis is the real oversell gatekeeper at reserve time, so what is corrupted is the bookkeeping counter, not actual inventory — and design doc §2.2's end-of-sale reconciliation is what catches it. The future persistence/application layer must wrap the load-mutate-save cycle in row-level locking (`SELECT ... FOR UPDATE`) or re-enable `@Version` on the JPA entity. Noted per plan-review finding (2026-09-15, NOTED) — no domain code change.
- MEDIUM: `StockCachePort.tryReserve(...)` takes `maxPerUser` as a parameter rather than having the adapter look it up. This keeps the port stateless and the domain free of a `TicketTypeRepository` call inside the cache adapter, but it means the caller (`ReserveTicketUseCase`) must load the `TicketType` first and pass a consistent value. If an organizer edits `max_per_user` mid-sale, in-flight reservations use the old value — acceptable, since the Redis counter itself is the durable record and the limit is a soft business rule, not an oversell guard.
- MEDIUM: the `releaseSale()` / `StockCachePort.release()` split is a real correctness trap for the future application layer — they compensate different things (Postgres counter vs Redis counter) and calling the wrong one silently corrupts the sale. Mitigated by the normative compensation table in Design Detail, which names every flow and says which of the two it calls; the refund row is the only one calling both. The methods are also structurally hard to confuse: different names, different signatures (`releaseSale(int)` vs `release(UUID, UUID, int)`), different types. Reviewed and accepted as adequate for a domain-layer plan (2026-09-15, NOTED) — a stronger guard (one method per use case) needs the `application` module, which does not exist yet.
- LOW: `version` field is included in the entity (mirroring the schema's optimistic-lock column) but is never mutated by domain code — bumping it is a JPA/persistence-layer concern (`@Version`) that belongs to the future `infrastructure` module's `TicketTypeJpaEntity`, not to this pure-Java model. Under §2.4 it no longer guards inventory at all, only concurrent organizer edits.

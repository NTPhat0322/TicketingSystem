# Phase 6: Ticket Aggregate

## Requirements
Model the `Ticket` aggregate root — issued only after payment success (per design doc §2.2 step 3), with `issueFor()` static factory and a guarded `checkIn()`/`cancel()` lifecycle.

## Steps
1. Implement `TicketStatus` enum (`ISSUED`, `CHECKED_IN`, `CANCELLED`) with transition table.
2. Implement `Ticket` entity mirroring the `tickets` table: `id`, `ticketCode`, `orderItemId`, `ticketTypeId`, `ownerUserId`, `status`, `issuedAt`, `checkedInAt`.
3. Implement the static factory `Ticket.issueFor(...)`.
4. Implement guarded methods `checkIn()` and `cancel()`.
5. Implement the three `Ticket`-specific exceptions.
6. Implement `TicketRepository` port (`save`, `findById`, `findByTicketCode`, `findAllByOrderItemId`).
7. Write `TicketTest` and `TicketStatusTest`; run `mvn test -pl domain` and confirm all tests pass.

## Files to Create
- `domain/src/main/java/com/tienphat/domain/model/Ticket.java`
- `domain/src/main/java/com/tienphat/domain/model/TicketStatus.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidTicketDataException.java`
- `domain/src/main/java/com/tienphat/domain/exception/TicketAlreadyCheckedInException.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidTicketStateException.java`
- `domain/src/main/java/com/tienphat/domain/repository/TicketRepository.java`
- `domain/src/test/java/com/tienphat/domain/model/TicketTest.java`
- `domain/src/test/java/com/tienphat/domain/model/TicketStatusTest.java`

## Design Detail

**`TicketStatus` transition table**:
| From | Allowed To |
|---|---|
| `ISSUED` | `CHECKED_IN`, `CANCELLED` |
| `CHECKED_IN` | *(none — terminal)* |
| `CANCELLED` | *(none — terminal)* |

**`Ticket`** (`@Getter`, `@Builder(access = AccessLevel.PRIVATE)`, `@EqualsAndHashCode(of = "id")`):
- `static Ticket issueFor(UUID orderItemId, UUID ticketTypeId, UUID ownerUserId, String ticketCode)`: throws `InvalidTicketDataException` if `ticketCode` is blank; `id = UUID.randomUUID()` (plain v4 — no B-tree insert-order concern documented for tickets, unlike `Order.id`); `status = ISSUED`; `issuedAt = Instant.now()`; `checkedInAt = null`. **Called by the future `ConfirmPaymentUseCase`, not by `Order`** — per design doc §2.2, tickets exist only from `PAID` onward.
- `void checkIn()`: if `status == CHECKED_IN`, throws `TicketAlreadyCheckedInException`; else if `status == CANCELLED`, throws `InvalidTicketStateException`; else `status = CHECKED_IN`, `checkedInAt = Instant.now()`.
- `void cancel()`: `ISSUED → CANCELLED`; else throws `InvalidTicketStateException` (covers both `CHECKED_IN` and already-`CANCELLED`).

**`TicketRepository`**: `Ticket save(Ticket ticket)`, `Optional<Ticket> findById(UUID id)`, `Optional<Ticket> findByTicketCode(String ticketCode)`, `List<Ticket> findAllByOrderItemId(UUID orderItemId)`.

## Unit Tests
**`TicketTest`**:
- `issueFor()` succeeds, `status == ISSUED`, `checkedInAt == null`.
- `issueFor()` throws `InvalidTicketDataException` when `ticketCode` is blank.
- `checkIn()` succeeds from `ISSUED`, sets `checkedInAt`.
- `checkIn()` throws `TicketAlreadyCheckedInException` when already `CHECKED_IN`.
- `checkIn()` throws `InvalidTicketStateException` when `CANCELLED`.
- `cancel()` succeeds from `ISSUED`.
- `cancel()` throws `InvalidTicketStateException` from `CHECKED_IN`.

**`TicketStatusTest`**: exhaustive transition-table coverage.

## Success Criteria (Pass/Fail)
- `mvn test -pl domain` passes, including all `TicketTest`/`TicketStatusTest` cases.
- `Ticket` has no public setter; `checkIn()`/`cancel()` are the only mutation entry points.
- `TicketAlreadyCheckedInException` and `InvalidTicketStateException` are distinct classes (not one reused for both cases), matching the precise-exception-per-invariant convention.

## Risks
- None specific beyond the shared conventions already covered in Phase 1's Risks. Note for future phases: `Ticket.issueFor()` deliberately has no knowledge of `Order`/`Payment` — the orchestration ("on payment success, issue N tickets for an order item") is application-layer, out of scope here.

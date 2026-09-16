# Phase 3: Event Aggregate

## Requirements
Model the `Event` aggregate root with a full `EventStatus` state machine (`DRAFT → PUBLISHED → ON_SALE → CLOSED`, plus `CANCELLED` from any non-terminal state) and schedule validation at creation.

## Steps
1. Implement `EventStatus` enum with an `EnumMap`-backed transition table and `canTransitionTo(EventStatus target)`.
2. Implement `Event` entity mirroring the `events` table: `id`, `organizerId`, `name`, `description`, `venueName`, `startTime`, `endTime`, `saleStartTime`, `saleEndTime`, `status`, `createdAt`, `updatedAt`. **No `flashSaleMode` field** — design doc §2.4 removed the flag; every event uses the same Redis+Lua inventory path, so there is nothing for the entity to branch on.
3. Implement the static factory `Event.create(...)` with schedule-ordering validation.
4. Implement guarded transition methods `publish()`, `startSale()`, `close()`, `cancel()`.
5. Implement `InvalidEventStateException` (illegal transition) and `InvalidEventScheduleException` (bad time ordering).
6. Implement `EventRepository` port (`save`, `findById`).
7. Write `EventTest` (factory + all four guarded methods) and `EventStatusTest` (exhaustive transition-table coverage).
8. Run `mvn test -pl domain` and confirm all tests pass.

## Files to Create
- `domain/src/main/java/com/tienphat/domain/model/Event.java`
- `domain/src/main/java/com/tienphat/domain/model/EventStatus.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidEventStateException.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidEventScheduleException.java`
- `domain/src/main/java/com/tienphat/domain/repository/EventRepository.java`
- `domain/src/test/java/com/tienphat/domain/model/EventTest.java`
- `domain/src/test/java/com/tienphat/domain/model/EventStatusTest.java`

## Design Detail

**`EventStatus` transition table**:
| From | Allowed To |
|---|---|
| `DRAFT` | `PUBLISHED`, `CANCELLED` |
| `PUBLISHED` | `ON_SALE`, `CANCELLED` |
| `ON_SALE` | `CLOSED`, `CANCELLED` |
| `CLOSED` | *(none — terminal)* |
| `CANCELLED` | *(none — terminal)* |

**`Event`** (`@Getter`, `@Builder(access = AccessLevel.PRIVATE)`, `@EqualsAndHashCode(of = "id")`):
- `static Event create(UUID id, UUID organizerId, String name, String description, String venueName, Instant startTime, Instant endTime, Instant saleStartTime, Instant saleEndTime)`: throws `InvalidEventScheduleException` if `!startTime.isBefore(endTime)` or `!saleStartTime.isBefore(saleEndTime)`; status starts at `DRAFT`; `createdAt = updatedAt = Instant.now()`.
- `void publish()`: `DRAFT → PUBLISHED`; else throws `InvalidEventStateException`.
- `void startSale()`: `PUBLISHED → ON_SALE`; else throws `InvalidEventStateException`.
- `void close()`: `ON_SALE → CLOSED`; else throws `InvalidEventStateException`.
- `void cancel()`: any of `DRAFT`/`PUBLISHED`/`ON_SALE` `→ CANCELLED`; else throws `InvalidEventStateException` (e.g. already `CLOSED`).
- All guarded methods bump `updatedAt`.

**`EventRepository`**: `Event save(Event event)`, `Optional<Event> findById(UUID id)`.

## Unit Tests
**`EventTest`**:
- `create()` succeeds with valid schedule, status is `DRAFT`.
- `create()` throws `InvalidEventScheduleException` when `startTime >= endTime`.
- `create()` throws `InvalidEventScheduleException` when `saleStartTime >= saleEndTime`.
- `publish()` succeeds from `DRAFT`.
- `publish()` throws `InvalidEventStateException` from `ON_SALE`.
- `startSale()` succeeds from `PUBLISHED`; throws from `DRAFT`.
- `close()` succeeds from `ON_SALE`; throws from `PUBLISHED`.
- `cancel()` succeeds from `PUBLISHED`; throws `InvalidEventStateException` from `CLOSED`.

**`EventStatusTest`**:
- For every `(from, to)` pair in the table above, `canTransitionTo` returns `true`.
- For every other pair, `canTransitionTo` returns `false` (exhaustive negative check over the full Cartesian product minus the allowed pairs).

## Success Criteria (Pass/Fail)
- `mvn test -pl domain` passes, including all `EventTest`/`EventStatusTest` cases.
- `Event` has no public setter; every state change goes through a guarded method.
- `EventStatusTest` exhaustively covers the full 5×5 transition matrix (25 pairs), not just the positive cases.

## Risks
- LOW: design doc §2.4 makes Redis cache-warming a precondition for selling, and §6 leaves open whether that warming is triggered by `startSale()` or by a separate scheduled job. `Event.startSale()` here stays a pure in-memory status transition either way — it cannot call `StockCachePort` without the domain reaching for a port it does not own. Whichever trigger is chosen, the orchestration belongs in the future `StartSaleUseCase`.
- Schedule validation (`startTime < endTime`, `saleStartTime < saleEndTime`) does not check `saleEndTime <= startTime` (i.e. sales could theoretically extend past the event start) — deliberately left unenforced since design doc §4 doesn't state this as a hard invariant; flagged here in case the application layer wants to add it.

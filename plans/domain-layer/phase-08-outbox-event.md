# Phase 8: OutboxEvent

## Requirements
Model `OutboxEvent`, the transactional-outbox row (design doc §2.5) — infra-adjacent in purpose but domain-modeled like every other aggregate, with a `retry()` transition supporting the publisher poller's retry loop.

## Steps
1. Implement `AggregateType` enum (`ORDER`, `PAYMENT`) and `OutboxEventType` enum (`ORDER_CREATED`, `ORDER_EXPIRED`, `PAYMENT_SUCCESS`) — the exact 3 event types design doc §4 lists.
2. Implement `OutboxEventStatus` enum (`PENDING`, `PUBLISHED`, `FAILED`) with transition table, including `FAILED → PENDING` for publisher retries.
3. Implement `OutboxEvent` entity mirroring the `outbox_events` table: `id`, `aggregateType`, `aggregateId`, `eventType`, `payload` (raw JSON `String` — domain does not depend on a JSON library), `status`, `createdAt`, `publishedAt`.
4. Implement the static factory `OutboxEvent.record(...)`.
5. Implement guarded methods `markPublished()`, `markFailed()`, `retry()`.
6. Implement the two `OutboxEvent`-specific exceptions.
7. Implement `OutboxEventRepository` port (`save`, `findById`, `findAllPending` — the publisher poller's query).
8. Write `OutboxEventTest` and `OutboxEventStatusTest`; run `mvn test -pl domain` and confirm the full module test suite passes.

## Files to Create
- `domain/src/main/java/com/tienphat/domain/model/AggregateType.java`
- `domain/src/main/java/com/tienphat/domain/model/OutboxEventType.java`
- `domain/src/main/java/com/tienphat/domain/model/OutboxEventStatus.java`
- `domain/src/main/java/com/tienphat/domain/model/OutboxEvent.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidOutboxEventDataException.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidOutboxEventStateException.java`
- `domain/src/main/java/com/tienphat/domain/repository/OutboxEventRepository.java`
- `domain/src/test/java/com/tienphat/domain/model/OutboxEventTest.java`
- `domain/src/test/java/com/tienphat/domain/model/OutboxEventStatusTest.java`

## Design Detail

**`OutboxEventStatus` transition table**:
| From | Allowed To |
|---|---|
| `PENDING` | `PUBLISHED`, `FAILED` |
| `PUBLISHED` | *(none — terminal)* |
| `FAILED` | `PENDING` (publisher retry) |

**`OutboxEvent`** (`@Getter`, `@Builder(access = AccessLevel.PRIVATE)`, `@EqualsAndHashCode(of = "id")`):
- `static OutboxEvent record(AggregateType aggregateType, UUID aggregateId, OutboxEventType eventType, String payload)`: throws `InvalidOutboxEventDataException` if `payload` is blank or `aggregateType`/`eventType` is `null`; `id = UUID.randomUUID()`; `status = PENDING`; `createdAt = Instant.now()`; `publishedAt = null`. **Called in the same transaction as the triggering write** (e.g. order-create worker, `ConfirmPaymentUseCase`) per design doc §2.5 — no direct MQ publish.
- `void markPublished()`: `PENDING → PUBLISHED`; else throws `InvalidOutboxEventStateException`; sets `publishedAt = Instant.now()`.
- `void markFailed()`: `PENDING → FAILED`; else throws `InvalidOutboxEventStateException`.
- `void retry()`: `FAILED → PENDING`; else throws `InvalidOutboxEventStateException`; resets the row for the poller's next attempt.

**`OutboxEventRepository`**: `OutboxEvent save(OutboxEvent event)`, `Optional<OutboxEvent> findById(UUID id)`, `List<OutboxEvent> findAllPending()` — used by the future publisher poller job.

## Unit Tests
**`OutboxEventTest`**:
- `record()` succeeds, `status == PENDING`, `publishedAt == null`.
- `record()` throws `InvalidOutboxEventDataException` when `payload` is blank.
- `record()` throws `InvalidOutboxEventDataException` when `eventType` is `null`.
- `markPublished()` succeeds from `PENDING`, sets `publishedAt`.
- `markPublished()` throws `InvalidOutboxEventStateException` when already `PUBLISHED`.
- `markFailed()` succeeds from `PENDING`.
- `markFailed()` throws `InvalidOutboxEventStateException` when called on an already-`PUBLISHED` event. Added per plan-review finding (2026-09-15, ACCEPTED) — keeps `markFailed()` consistent with the negative-case coverage every other guarded method in this plan has.
- `retry()` succeeds from `FAILED`, resets to `PENDING`.
- `retry()` throws `InvalidOutboxEventStateException` when called on a `PENDING` event.

**`OutboxEventStatusTest`**: exhaustive transition-table coverage.

## Success Criteria (Pass/Fail)
- `mvn test -pl domain` passes, including all `OutboxEventTest`/`OutboxEventStatusTest` cases.
- `OutboxEvent` has no public setter.
- Full-module regression: `mvn test -pl domain` at the end of this phase runs **all** test classes from Phases 1–8 (not just this phase's) and all pass — this is the final phase, so this run is the plan's overall done criterion.
- No `@Setter`, no `@Data`, and no public no-private-access `@Builder` exists anywhere under `domain/src/main/java` — spot-check via `grep -rn "@Setter\|@Data" domain/src/main/java` returning zero matches, and `grep -rn "@Builder" domain/src/main/java` showing only `access = AccessLevel.PRIVATE` usages.

## Risks
- LOW: `payload` is a raw JSON `String` rather than a typed payload object, since `domain` has no JSON library dependency and shouldn't gain one just for this. The future infrastructure-layer publisher is responsible for serialization/deserialization; domain only stores and moves the opaque string.
- LOW: `OutboxEventType` enum is closed to the 3 values design doc §4 currently lists — adding a 4th outbox event type later requires an enum edit (see `plan.md` Risks for the same trade-off applied to `PaymentProvider`).

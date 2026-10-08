# Phase 3: Redis Lua reservation, Hash/Sorted Set intent store, and cache warming

## P1 Stories Satisfied

- Atomic reservation without oversell.
- Durable pending intent for asynchronous creation.
- Atomic, idempotent release foundation for expiry and reconciliation.

## Checklist

- [x] Atomic Lua reservation checks stock and per-user limit, then writes stock, user counter, hold, intent, and pending index together.
- [x] Idempotent Lua release restores stock and user allowance once and keeps the intent record for inspection.
- [x] Due intent reads load ids from the Sorted Set and payloads from Hashes.
- [x] Retry, enqueue confirmation, Order-created, expiry, release, and short claim/lease operations are implemented.
- [x] Start-sale cache warming seeds every TicketType before the Event becomes `ON_SALE`; missing stock never warms on demand.
- [x] Redis Testcontainers cover serialization, missing warm-up, TTL/recovery separation, state transitions, release replay, and concurrency limits.

## Affected Areas

- `infrastructure/src/main/java/com/tienphat/infrastructure/redis/`
- Redis adapter/port implementations from Phase 2
- `bootstrap/src/main/resources/application.yml` if property names need finalization
- Redis/Testcontainers integration tests

## Requirements

1. Implement a Lua reserve script that atomically:
   - verifies the stock key exists and has enough stock;
   - verifies the user counter will not exceed `maxPerUser`;
   - decrements stock and increments user allowance usage;
   - creates `hold:order:{orderId}` with reservation metadata and a short TTL;
   - creates `reservation:intent:{orderId}` with the full snapshot and `PENDING` state;
   - adds `orderId` to `reservation:pending` with `nextRetryAt` as score.
2. Implement an idempotent release Lua script. It must distinguish `ACTIVE/HELD` from `RELEASED` and atomically restore stock plus the user counter only once.
3. Implement due-intent reads using `ZRANGEBYSCORE`/the current Redis equivalent and Hash loads. Keep the Sorted Set as an index, not the source of the full payload.
4. Implement state changes for retry, enqueue confirmation, Order-created completion, expiry, and release. Do not give the short-lived hold key the same TTL as the recovery intent.
5. Implement cache warming from TicketType `totalQuantity` before sale. The selected first implementation should expose this through the sale-preparation application action or the scheduled trigger answered in the review questions; warm-on-miss is forbidden.

## Implementation Steps

1. [x] Add key builders and field constants in one adapter package.
2. [x] Add Lua script resources under infrastructure and load them as `DefaultRedisScript` values with explicit result types.
3. [x] Implement reserve/release/intent operations using `StringRedisTemplate` or an equivalent typed template; use explicit serialization for UUIDs, timestamps, and amounts.
4. [x] Implement retry scheduling with `ZADD` and completion cleanup with `ZREM`; only remove a due member after the caller has a confirmed publish or terminal release.
5. [x] Add a short Redis claim/lease per order for multiple relay instances; keep idempotent duplicate handling as the correctness backstop.
6. [x] Add warm-up service and tests that prove an unwarmed key fails closed and a warm-up before holds seeds exactly `totalQuantity`.

## Invariants

- A successful Lua result changes stock, user limit, hold metadata, and pending intent together.
- A failed Lua result changes none of those values.
- `ZREM` never restores stock; release is a separate idempotent Lua operation.
- Release cannot decrement a user counter below zero or increment stock twice for one reservation.
- The intent remains recoverable after the hold metadata expires until reconciliation has decided to release or complete it.

## Success Criteria

- [x] A real Redis integration test with concurrent callers accepts no more than the warmed stock quantity and never produces a negative stock value.
- [x] A max-per-user concurrency test accepts no more than the configured user limit.
- [x] Replaying the release script returns success only once and leaves final stock/counter values correct.
- [x] A caller returning immediately after the Lua call leaves a discoverable intent in the Hash/Sorted Set, covering the crash window before relay publication.
- [x] A missing stock key produces a fail-closed business result and no intent.

## Test Strategy

- Script-level tests against a real Redis Testcontainer, not an in-memory fake.
- Concurrent executor tests with a small stock value and many callers.
- Serialization/round-trip tests for UUID, `Instant`, amount, state, and retry fields.
- Warm-up and release idempotency tests.

## Rollback Notes

If Redis scripts fail in production-like verification, disable the reservation endpoint/fail closed rather than falling back to PostgreSQL. Roll back only the adapter/configuration and preserve the domain port contract for a corrected script.

## Risks

- Redis persistence/HA is not solved by this code phase; local Docker proves behavior, not production durability.
- Script key-slot behavior becomes relevant if Redis Cluster is introduced later; the first implementation targets standalone Redis and must use consistent key naming if Cluster support is added.


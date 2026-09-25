# Brainstorm: Ticket purchase and asynchronous reservation

**Date:** 2026-09-25

## Ideas Explored

- **Reservation flow**: synchronous Order creation versus Redis reservation followed by asynchronous Order creation. Synchronous creation is simpler, but the chosen design keeps the high-contention inventory path short and lets the database absorb writes through RabbitMQ.
- **RabbitMQ publish failure**: fail closed immediately versus keeping the request in `CREATING` and retrying in the background. The chosen design keeps a durable reservation intent and lets the client poll the Order status.
- **Reservation intent storage**: PostgreSQL outbox versus Redis. PostgreSQL cannot be atomically committed together with the Redis stock decrement; Redis can record the hold and pending intent in the same Lua script.
- **Pending-intent structure**: Redis Stream versus a simpler Redis Hash plus Sorted Set. Redis Stream was deferred for the first implementation to reduce operational and code complexity.
- **Transactional Outbox boundary**: using `OutboxEvent` for the initial Redis-to-RabbitMQ command versus using it after a PostgreSQL business transaction. The existing Outbox remains a PostgreSQL outbound-event mechanism, not the initial reservation intent.

## User's Direction

The user chose the following MVP architecture:

```text
Redis Lua:
  check stock and max_per_user
  decrement stock and increment user counter
  create hold
  create pending reservation intent

API:
  return 202 Accepted with orderId and CREATING status

Relay:
  read reservation:intent:{orderId} and reservation:pending
  publish an order-create message to RabbitMQ
  remove the Sorted Set entry only after publisher confirmation

Worker:
  create Order and OrderItem idempotently by orderId

RabbitMQ TTL + DLX:
  signal hold expiry
  expiry worker conditionally changes PENDING_PAYMENT to EXPIRED
  release Redis stock atomically and idempotently

Reconciliation job:
  retry stuck intents and repair lost/stale messages

PostgreSQL Outbox:
  keep the existing OutboxEvent for events caused by committed Order/Payment changes
```

The first implementation uses:

```text
reservation:intent:{orderId}  -> Redis Hash with intent details and state
reservation:pending           -> Redis Sorted Set, score = nextRetryAt
```

Redis Stream, a separate reservation domain aggregate, and a PostgreSQL reservation-intent table are out of the first scope.

The user later confirmed the remaining product-level defaults:

- Cache warming is triggered by an explicit `StartSaleUseCase` in the MVP; a scheduled `saleStartTime` job is deferred.
- The REST contract is `POST /api/v1/orders`, `GET /api/v1/orders/{orderId}`, and `POST /api/v1/payments/callback`.
- Payment uses a deterministic local/test adapter first; VNPAY/MOMO/Stripe integration is deferred.

## Decisions

1. Redis is the live source of truth for sale-time inventory. A missing or unavailable stock key fails closed; there is no fallback reservation path through PostgreSQL.
2. The application generates `orderId` before the Lua call so the same identifier travels through Redis, RabbitMQ, PostgreSQL, polling, and idempotency checks.
3. The Lua script must atomically perform the stock check, per-user limit check, stock decrement, user-counter increment, hold creation, and pending-intent registration.
4. The API returns `202 Accepted` and `CREATING` after the Redis operation succeeds. The client polls for the Order to become `PENDING_PAYMENT`.
5. RabbitMQ delivery is at-least-once. Duplicate messages are expected and are absorbed by a unique Order identifier and an idempotent worker.
6. TTL + DLX is only the expiry signal. The expiry worker must verify the current Order status and use an idempotent Lua release operation; Redis key expiry alone must never be treated as stock release.
7. `sold_quantity` is updated only after successful payment. Order creation and expiry do not update it.
8. The existing `OutboxEvent` and `OutboxEventType` remain PostgreSQL-side infrastructure. They are not used to represent the Redis reservation intent.

## Open Questions

No blocking architecture questions remain for the MVP. Exact REST DTOs, retry intervals, queue names, and the cache-warming trigger can be finalized during planning while preserving the decisions above.

## Risks

- **HIGH** — Redis is intentionally a single source of truth during sale. Redis persistence/HA and cache warming must be handled operationally before production use; Redis failure must stop reservations rather than fall back to a second stock algorithm.
- **MEDIUM** — A crash after RabbitMQ accepts a message but before Redis state is updated can produce a duplicate publish. This is acceptable only because the Order worker is idempotent by `orderId`.
- **MEDIUM** — The hold metadata and the pending intent have different lifecycles. The intent must remain available long enough for retry/reconciliation; a short hold TTL must not erase the only release information.
- **MEDIUM** — Payment and expiry can race. PostgreSQL status transitions must be conditional, and Redis release must occur only when the transition to `EXPIRED` actually succeeded.
- **LOW** — Hash + Sorted Set requires the relay to implement claiming/retry bookkeeping itself. Redis Stream can be introduced later if relay throughput or recovery requirements justify it.

# Plan: Order history, my tickets, manual close-sale, buyer cancellation

Status: Done — implemented 2026-10-10, `mvn clean install` green
Date: 2026-10-10
Mode: Hard
Test: default

## Goal

Close the post-purchase gaps left by `ticket-purchase-hold`:

```text
buyer   → GET  /api/v1/orders?page&size            order history (newest first)
buyer   → GET  /api/v1/orders/{orderId}/tickets    tickets of one paid order
buyer   → GET  /api/v1/tickets?page&size           all my tickets, enriched with tier + event
buyer   → POST /api/v1/orders/{orderId}/cancel     PENDING_PAYMENT → CANCELLED + Redis release
org/adm → POST /api/v1/events/{id}/close-sale      ON_SALE → CLOSED (manual, no scheduler)
```

## Scope Challenge

```text
Exists?      Partial. Order.cancel(), Event.close(), StockCachePort.release (idempotent by orderId)
             and the ownership checks already exist. No list queries, no ticket read API,
             no cancel/close use cases or endpoints.
Minimum?     Read-only paging queries + two state-transition use cases reusing existing domain
             transitions and the existing Redis release script. No new Redis keys, no Lua changes,
             no new outbox types, no scheduler.
Complexity?  Hard — touches all five modules, and cancellation races with payment callbacks,
             TTL/DLX expiry, and a post-commit Redis release that can fail.
Test mode?   default — unit + WebMvc + Testcontainers repository tests per phase, E2E at the end.
```

## Non-Goals

- Scheduler that closes sales at `saleEndTime` (manual endpoint only, per request).
- Closing `TicketType`s or deleting Redis stock keys on close-sale.
- `ORDER_CANCELLED` outbox event (no consumer exists yet; add with the notification consumer).
- Refunds, cancelling PAID orders, event cancellation cascade, check-in.
- Listing Redis-only `CREATING` reservations in order history.
- ADMIN-wide order/ticket search (list endpoints always return the caller's own data).

## Architecture Decisions

1. **History paging avoids collection-fetch pagination.** `findAllByUserId` pages order ids first
   (`createdAt desc, id desc` + count query), then loads those orders with `@EntityGraph(items)`
   and restores the id order in memory. Never `Page<Entity>` + fetch join (Hibernate would page in memory).
2. **Batch enrichment, no N+1.** Payments for a history page come from one `findAllByOrderIds`.
   Ticket results load ticket types and events with one `findAllByIds` each; a missing
   type/event leaves its name fields `null` instead of failing the page.
3. **Ownership.** List endpoints use `actor.userId()` only. Single-order endpoints
   (`/tickets`, `/cancel`) use `requireCanAccess` (owner or ADMIN), same as status polling.
4. **Close-sale is a pure DB transition.** `ReserveTicketUseCase` already rejects non-`ON_SALE`
   events with 409, so Redis is not touched. Live holds may still be paid until `expiresAt`.
   A reservation that read `ON_SALE` just before the close commits may still succeed — accepted.
5. **Cancel = locked DB transition, then post-commit Redis release** (same shape as expiry):
   `CancelOrderTransaction` locks the order row; `CancelOrderUseCase` calls
   `stockCachePort.release(...)` after commit. Release restores stock + per-user allowance and
   sets the intent to `RELEASED`, which stops expiry/reconciliation scans.
6. **Cancel is idempotent.** Cancelling an already `CANCELLED` order returns 200 and re-runs the
   (idempotent) release, so a client retry repairs a failed Redis call.
7. **Safety net for a failed release.** `ExpireOrderTransaction` currently returns no release for
   `CANCELLED`/`FAILED`. Change it to return the release coordinates, so the TTL/DLX expiry message
   that is already scheduled for every order restores stock if the cancel-time release failed.
   Release and `markExpired` are both no-ops on an already released/terminal intent.
8. **Payment row stays `PENDING` on cancel**, mirroring expiry (`PaymentStatus` has no CANCELLED;
   `FAILED` would misreport a charge attempt). A later callback hits the existing
   "order is not PENDING_PAYMENT" guard → 409.
9. **Error mapping.** `InvalidOrderStateException` → `CONFLICT` (409) instead of the default 500.
   Cancel of `PAID`/`EXPIRED`/`FAILED` → 409. Close-sale from a non-`ON_SALE` state keeps the
   existing `InvalidEventStateException` → 400 convention.

## Concurrency Analysis

| Race | Outcome |
|------|---------|
| cancel ↔ payment callback | Both lock the order row. First commit wins; loser gets 409. Cancel locks only the order and reads payment without a lock, so it cannot deadlock with the payment→order lock order. |
| cancel ↔ TTL expiry | Both lock the order row. Expiry after cancel sees `CANCELLED` → idempotent release. Cancel after expiry → 409. |
| cancel while order is still `CREATING` | Order row absent → 409 "still being created", client retries after `PENDING_PAYMENT`. |
| worker `markOrderCreated` after cancel release | State script ignores `RELEASED` → no regression. |
| reserve ↔ close-sale | Narrow window where a request that already read `ON_SALE` takes a hold; accepted. |

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Persistence queries and error mapping](phase-01-persistence-queries.md) | Done |
| 2 | [Order history and my tickets API](phase-02-order-history-and-tickets-api.md) | Done |
| 3 | [Manual close-sale endpoint](phase-03-close-sale.md) | Done |
| 4 | [Buyer cancels a pending order](phase-04-cancel-order.md) | Done |
| 5 | [E2E and full verification](phase-05-e2e-verification.md) | Done |

Phases 2–4 depend on phase 1 and are independent of each other. Phase 5 depends on all.

## Success Criteria

- All five endpoints exist with OpenAPI security metadata and return the documented status codes.
- History and ticket list queries issue a constant number of SQL statements per page (no N+1).
- After cancel: order `CANCELLED`, Redis stock and the user's allowance restored, intent `RELEASED`,
  repeat cancel returns 200 without restoring stock twice, a payment callback returns 409.
- After close-sale: event `CLOSED`, new reservations rejected with 409.
- `mvn clean install` passes (Testcontainers: PostgreSQL, Redis, RabbitMQ).

## Risks

- `ddl-auto: update` creates the new indexes on startup; there is no migration tool to version them.
- Intent hashes have no TTL; terminal intents stay in Redis. Pre-existing, discussed with item 8.
- If the post-commit Redis release throws, the cancel request fails (5xx) although the DB is already
  `CANCELLED`. A retry returns 200 and releases; otherwise the expiry message does it at `expiresAt`.
  Documented in the endpoint description.

## Open Questions (defaults applied unless changed)

1. Close-sale also closes all `TicketType`s / clears Redis stock? **Default: no.**
2. Record an `ORDER_CANCELLED` outbox event now? **Default: no**, add with the notification consumer.
3. ADMIN on `GET /orders` and `GET /tickets` sees only their own data? **Default: yes.**
4. `GET /orders/{id}/tickets` for a Redis-only `CREATING` order: 404 vs empty list? **Default: 404.**

## Delivery

No commit by the agent. Suggested branch `feature/order-history-cancel-close-sale`.

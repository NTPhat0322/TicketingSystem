# Plan: Ticket purchase and asynchronous reservation

Status: Ready
Date: 2026-09-25
Mode: Hard
Test: default

## Goal

Implement the first complete ticket-purchase path for one `TicketType` per request:

```text
authenticated customer
  → Redis Lua atomic reservation
  → Redis Hash + Sorted Set pending intent
  → 202 CREATING
  → RabbitMQ order-create command
  → idempotent Order worker
  → PENDING_PAYMENT polling
  → payment callback
  → PAID + Ticket rows + sold_quantity + OutboxEvent
  → TTL/DLX expiry and reconciliation for failures
```

The plan preserves the existing multi-module Clean Architecture and keeps Redis as the live sale-time inventory source of truth.

## Scope Challenge

```text
Exists?      Partial scaffolding only. Domain models and ports exist, but the runtime flow,
             persistence adapters, Redis/RabbitMQ adapters, controllers, and workers do not.
Minimum?     One TicketType per reservation, 202 CREATING, async Order creation, status polling,
             expiry/release, generic payment callback, ticket issuance, and Outbox persistence.
Complexity?  Hard. The feature crosses all five modules, two external services, database
             transactions, concurrency, at-least-once delivery, and recovery paths.
Test mode?   default. Every phase includes unit/integration tests; strict red-first TDD is not
             required for every adapter, but concurrency and failure paths are mandatory gates.
```

## Non-Goals

- Redis Stream consumer groups in the first implementation.
- A separate PostgreSQL reservation-intent table or reservation domain aggregate.
- Multi-TicketType carts, manual cancellation, refunds, chargebacks, or check-in.
- A real external payment-provider integration; use a provider port and a local/test adapter.
- Redis Sentinel/Cluster production topology and capacity tuning.
- Splitting the current monolith into services.

## Repository Evidence

- `domain` already contains `Order`, `OrderItem`, `Payment`, `Ticket`, `TicketType`, `OutboxEvent`, repositories, and `StockCachePort`.
- `Order.create(...)` currently generates its own UUID; the async flow needs the application to generate `orderId` before Redis, so the factory contract must be extended.
- `StockCachePort` already defines atomic reserve/release/warm-up concepts, but there is no implementation.
- `application` has no Order, Payment, Ticket, reservation, expiry, or reconciliation use cases yet.
- `infrastructure` currently has only User/Event/TicketType/refresh-token persistence adapters. There are no Order, Payment, Ticket, Outbox, Redis, or RabbitMQ adapters.
- `presentation.UseCaseConfig` manually wires use cases, so new application services must be registered there.
- `bootstrap` uses `ddl-auto: update`; there is no Flyway/Liquibase migration pipeline to extend in this feature.
- `docker-compose.yml` currently starts only PostgreSQL and the application; Redis and RabbitMQ must be added.
- Existing tests use JUnit/Mockito for unit tests and Testcontainers PostgreSQL for integration/smoke tests. Docker is already a test prerequisite.

## Architecture Decisions

1. **Atomic reservation boundary:** evolve the existing `StockCachePort` contract so the Redis adapter receives the pre-generated `orderId`, reservation metadata, and expiry time. The adapter's Lua script performs stock check, user-limit check, stock decrement, user-counter increment, hold creation, and pending-intent registration in one Redis execution. Do not split those writes into separate application calls.
2. **Intent storage:** use `reservation:intent:{orderId}` as a Redis Hash and `reservation:pending` as a Sorted Set whose score is `nextRetryAt`. The Hash is the record; the Sorted Set is the retry index.
3. **Intent lifecycle:** keep intent metadata longer than the short-lived hold metadata. A hold TTL must not erase the only information needed to release stock after a relay crash. Terminal intent cleanup is a separate retention task.
4. **Relay delivery:** a scheduled relay reads due Sorted Set members, loads the Hash, publishes a durable RabbitMQ command with publisher confirms, and removes the member only after confirmation. A crash can cause duplicate publish; the Order worker must absorb it by `orderId`.
5. **RabbitMQ topology:** use a durable order-create queue and a durable expiry delay queue with a Dead Letter Exchange. Use per-message expiration derived from `expiresAt` so different TicketTypes can have different hold durations; the DLX is only the expiry signal.
6. **Order identity and price:** the application generates `orderId` before Redis. The reservation intent captures `eventId`, `ticketTypeId`, `quantity`, `unitPrice`, `holdDurationSec`, and `expiresAt` so a later TicketType price edit cannot change the buyer's reserved price.
7. **Order status:** the client receives `CREATING` while the Order row is absent, then polls `GET /api/v1/orders/{orderId}` until the persisted lifecycle is `PENDING_PAYMENT`, `PAID`, or `EXPIRED`.
8. **Database transactions:** application use cases use the existing Spring transaction convention. Multi-aggregate payment confirmation writes Payment, Order, TicketType, Tickets, and OutboxEvent in one PostgreSQL transaction. Redis release after expiry is performed only after the conditional database transition commits; reconciliation repairs a release failure.
9. **Expiry race:** expiry changes only `PENDING_PAYMENT → EXPIRED`. Payment confirmation locks/guards the same state transition. If payment wins, expiry does not release inventory; if expiry wins, a later payment callback is rejected.
10. **Outbox boundary:** `OutboxEvent` is recorded only with committed PostgreSQL business changes (`ORDER_CREATED`, `ORDER_EXPIRED`, `PAYMENT_SUCCESS`). It is not the initial Redis reservation intent.
11. **Fail closed:** missing/unavailable Redis or an unwarmed stock key rejects reservation. There is no PostgreSQL fallback reservation path.
12. **Cache warming trigger:** the MVP uses an explicit `StartSaleUseCase`. It warms every TicketType for the Event first, then calls `Event.startSale()`. A scheduled job based on `saleStartTime` is a later enhancement.
13. **Local payment:** the first implementation exposes a provider-neutral payment port and callback contract, backed by a deterministic local/test adapter. VNPAY/MOMO/Stripe credentials, SDKs, and signatures are a later integration.
14. **REST contract:** the MVP uses `POST /api/v1/orders` for reservation, `GET /api/v1/orders/{orderId}` for polling, and `POST /api/v1/payments/callback` for the provider-neutral callback. The `StartSaleUseCase` is an application action; exposing it as another public endpoint is outside this feature's three-endpoint contract.

## Primary and Alternative Approaches

### Selected: Redis Hash + Sorted Set + RabbitMQ

This is the smallest design that keeps the user's chosen `CREATING` flow recoverable without introducing Redis Streams. It requires explicit retry/claim bookkeeping, but duplicate messages are already handled by the required idempotent worker.

### Rejected alternatives

- **Synchronous Order insert after Redis:** simpler, but it returns to the Redis↔PostgreSQL crash window on the request path and does not satisfy the selected asynchronous `CREATING` design.
- **PostgreSQL reservation intent/outbox:** auditable and familiar, but its row cannot be committed atomically with the Redis stock decrement. It moves rather than removes the failure gap.
- **Redis Stream:** stronger pending-entry/reclaim primitives, but additional operational and code complexity is unnecessary for the first implementation. It remains a future upgrade if relay throughput or recovery requires it.

## Phase Order

- [ ] Phase 1: Runtime dependencies, Docker services, and integration-test foundation
- [ ] Phase 2: Domain/application reservation contracts and Order status API model
- [ ] Phase 3: Redis Lua reservation, Hash/Sorted Set intent store, and cache warming
- [ ] Phase 4: PostgreSQL Order/Payment/Ticket/Outbox persistence
- [ ] Phase 5: RabbitMQ relay, Order worker, reservation/status REST flow
- [ ] Phase 6: TTL/DLX expiry, idempotent release, and reconciliation
- [ ] Phase 7: Payment initiation/callback, ticket issuance, and Outbox publisher
- [ ] Phase 8: Full Docker-backed E2E, concurrency, and failure verification

## P1 Story Coverage

| Spec story | Covered by |
|---|---|
| Atomic customer reservation without oversell | Phases 2, 3, 5, 8 |
| Immediate `202` with `CREATING` | Phases 2, 5, 8 |
| Client polling to `PENDING_PAYMENT`/terminal state | Phases 2, 5, 8 |
| Retry after RabbitMQ publish interruption | Phases 3, 5, 6, 8 |
| Idempotent Order worker | Phases 4, 5, 8 |
| TTL/DLX expiry and one-time release | Phases 3, 6, 8 |
| Payment success, sold quantity, Tickets, Outbox | Phases 4, 7, 8 |

## Validation Commands

Run after the relevant phases, from the repository root in PowerShell:

```powershell
./mvnw.cmd -pl domain,application -am test
./mvnw.cmd -pl infrastructure -am test
./mvnw.cmd -pl presentation -am test
./mvnw.cmd -pl bootstrap -am test -Dsurefire.failIfNoSpecifiedTests=false
./mvnw.cmd test
docker compose up -d --build
docker compose ps
docker compose logs --no-color app
Invoke-WebRequest http://localhost:8080/v3/api-docs
```

The Redis/Rabbit integration tests require Docker. The final E2E phase must run against real PostgreSQL, Redis, and RabbitMQ containers; mocked broker tests alone are insufficient for the at-least-once and expiry claims.

## Risks and Mitigations

- **HIGH — Redis is the live inventory truth.** Mitigation: fail closed, require cache warming before sale, test stock invariants under concurrency, and document AOF/HA as a production prerequisite.
- **HIGH — Redis and PostgreSQL cannot share one transaction.** Mitigation: Redis intent retention, RabbitMQ publisher confirms, idempotent Order creation, conditional expiry, and reconciliation.
- **HIGH — Payment/expiry race.** Mitigation: row locks or conditional updates on the Order/Payment/TicketType repositories and release only after a committed `EXPIRED` transition.
- **MEDIUM — Multiple relay instances may read the same due member.** Mitigation: short Redis claim/lease per `orderId`; tolerate any remaining duplicate with Order idempotency.
- **MEDIUM — Per-TicketType hold durations and Rabbit TTL semantics.** Mitigation: use per-message expiration and include `expiresAt` in every command; the worker double-checks the persisted deadline.
- **MEDIUM — Existing domain factory and persistence ports are partial scaffolding.** Mitigation: land contracts and persistence tests before wiring workers; never bypass domain factories with setters.
- **LOW — No real payment provider is selected.** Mitigation: provider-neutral port plus deterministic local/test adapter; provider signature verification is explicitly out of scope.

## Decisions Confirmed by User

- Cache warming uses the explicit `StartSaleUseCase` in the MVP; a scheduled `saleStartTime` job is deferred.
- The REST contract is `POST /api/v1/orders`, `GET /api/v1/orders/{orderId}`, and `POST /api/v1/payments/callback`.
- Payment uses a local/test adapter first; VNPAY/MOMO/Stripe are deferred.

## Handoff

The architecture and product-level defaults are now confirmed. The next implementation handoff is:

```text
Use $ck-cook --hard plans/ticket-purchase-hold/plan.md
```

# Plan: Ticket purchase and asynchronous reservation

Status: Complete
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
14. **REST contract:** the purchase flow uses `POST /api/v1/orders`, `GET /api/v1/orders/{orderId}`, and `POST /api/v1/payments/callback`. The approved Phase 9 operational additions are `POST /api/v1/events/{id}/start-sale` (ADMIN or owning ORGANIZER) and `POST /api/v1/admin/reservations/relay` (ADMIN only, one bounded due-intent batch). Scheduled relay and its retry/reconciliation paths remain the normal delivery mechanism.

## Primary and Alternative Approaches

### Selected: Redis Hash + Sorted Set + RabbitMQ

This is the smallest design that keeps the user's chosen `CREATING` flow recoverable without introducing Redis Streams. It requires explicit retry/claim bookkeeping, but duplicate messages are already handled by the required idempotent worker.

### Rejected alternatives

- **Synchronous Order insert after Redis:** simpler, but it returns to the Redis↔PostgreSQL crash window on the request path and does not satisfy the selected asynchronous `CREATING` design.
- **PostgreSQL reservation intent/outbox:** auditable and familiar, but its row cannot be committed atomically with the Redis stock decrement. It moves rather than removes the failure gap.
- **Redis Stream:** stronger pending-entry/reclaim primitives, but additional operational and code complexity is unnecessary for the first implementation. It remains a future upgrade if relay throughput or recovery requires it.

## Phase Order

- [x] Phase 1: Runtime dependencies, Docker services, and integration-test foundation
- [x] Phase 2: Domain/application reservation contracts and Order status API model
- [x] Phase 3: Redis Lua reservation, Hash/Sorted Set intent store, and cache warming
- [x] Phase 4: PostgreSQL Order/Payment/Ticket/Outbox persistence
- [x] Phase 5: RabbitMQ relay, Order worker, reservation/status REST flow
- [x] Phase 6: TTL/DLX expiry, idempotent release, and reconciliation
- [x] Phase 7: Payment initiation/callback, ticket issuance, and Outbox publisher
- [x] Phase 8: Full Docker-backed E2E, concurrency, and failure verification
- [x] Phase 9: Start-sale and admin reservation-relay API operations

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
| Event manager warms stock and starts sales | Phases 3, 9 |
| Admin can manually run a due reservation relay batch | Phases 5, 6, 9 |

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

## Session Notes
<!-- Updated by ck-cook; keep this section resumable. -->
**Last active:** 2026-10-08
**Phase in progress:** None — Phase 9 complete
**Status:** Phase 9 implemented and verified; final full-reactor run passed 512 tests.

### Phase 9 Decisions
- Exposed the existing `StartSaleUseCase` as `POST /api/v1/events/{id}/start-sale`; method security permits ADMIN/ORGANIZER, while the use case enforces organizer ownership and warms inventory before the event transition.
- Reject an Event that cannot transition to `ON_SALE` before touching Redis. This prevents a repeated start-sale call from reinitializing a missing stock key for an Event that is already selling.
- Added `POST /api/v1/admin/reservations/relay` for ADMIN only. It triggers exactly one existing bounded relay batch and returns the publisher-confirmed count; scheduled delivery, claims, retries, and reconciliation are unchanged.
- Kept messaging feature flags aligned: the admin relay route and use case are only wired when messaging and relay are enabled.

### Phase 9 Verification
- `RunReservationRelayUseCaseTest`: 2 tests passed (ADMIN success and non-admin rejection).
- Focused start-sale endpoint test: owner succeeds and warms Redis; foreign ORGANIZER and CUSTOMER receive 403.
- Focused purchase lifecycle: admin relay endpoint publishes one intent, rejects unauthenticated/non-admin callers, and a repeat trigger publishes zero duplicates.
- Start-sale use-case tests: 4 passed, including rejecting an already-ON_SALE Event before any Redis warm-up.
- Full Docker-backed lifecycle class: 7 passed.
- OpenAPI test confirms both new POST routes document Bearer authentication.
- `./mvnw.cmd -q -pl bootstrap -am "-Dsurefire.failIfNoSpecifiedTests=false" test` — final rerun passed: 512 tests, 0 failures/errors/skips (domain 227, application 100, infrastructure 78, presentation 88, bootstrap 19).
- `git diff --check` — pass.

### Phase 9 Error Report
1. The initial red test did not compile because the relay port/use case had not yet been implemented; adding them made the application tests pass.
2. The first HTTP red test got `404` for `/api/v1/events/{id}/start-sale`; this confirmed the route was absent. Adding the controller mapping and wiring made the endpoint test pass.
3. The first full-reactor run after adding the route reported 16 `EventControllerTest` context errors: the MVC test slice had no `StartSaleUseCase` bean. Added a `@MockitoBean` for the new controller dependency; the controller tests and final full reactor then passed.
4. One full-reactor run failed `OrderExpiryMessagingIntegrationTest.ttlExpiryDoesNotReleaseStockAfterPaymentWins` at the stock assertion (expected 2, got 3). The test's two-second Rabbit TTL makes this timing-sensitive; the single test passed in isolation and the subsequent full-reactor rerun passed. No failure remains reproduced; this existing expiry test remains a timing-sensitive check.
5. Review found that retrying start-sale for an already-`ON_SALE` Event could warm a missing Redis key before the domain rejected the transition. Added a domain precondition before warm-up and a regression test; focused and full tests pass afterward.
6. Full E2E intentionally logs injected Redis-release/RabbitMQ-publish failures and a malformed expiry message to verify recovery; these are asserted test scenarios, not unresolved runtime failures.

### Phase 9 Review
- Verdict: **WARNING** — authorization, lifecycle, Redis warming, relay behavior, and OpenAPI checks are covered, with no correctness/security finding remaining in the new routes.
- Residual operational risk: the admin relay endpoint runs a bounded batch synchronously and waits for RabbitMQ publisher confirms. During broker slowness, the HTTP call can outlive a client/proxy timeout even though scheduled retry remains available. Consider an asynchronous trigger/status contract before using this as a production operations endpoint.

### Phase 8 Decisions
- Added a full Spring/MockMvc lifecycle test backed by real PostgreSQL, Redis, and RabbitMQ containers; it covers immediate `CREATING`, worker transition, local payment callback, ticket issuance, and callback idempotency.
- Added HTTP concurrency coverage (30 requests against stock 6) and observed non-negative stock after each reservation, Rabbit duplicate-delivery coverage, retry after publish failure, expiry/payment race coverage, and recovery after a failed Redis release via reconciliation.
- Consumer beans now require explicit `ticketing.messaging.consumers-enabled=true`; the bootstrap default remains enabled, while infrastructure-only contexts do not instantiate consumers unless their application use cases are provided.
- User approved finalizing Phase 8 on 2026-10-07; Phase 8 is checked off and the feature plan is complete.

### Phase 8 Verification
- Targeted full-stack E2E: 6 tests passed with PostgreSQL, Redis, RabbitMQ, Spring HTTP/security, and generated OpenAPI.
- Targeted infrastructure messaging/topology tests: 5 tests passed, including order worker, TTL/DLX expiry, and durable Rabbit queue behavior.
- Full reactor `./mvnw.cmd -q test`: 506 tests, 0 failures, 0 errors, 0 skipped across 95 Surefire reports.
- `docker compose up -d --build`: passed. App, PostgreSQL, Redis, and RabbitMQ are running; the three dependency services report healthy.
- Live `curl.exe` checks: `/v3/api-docs` returned HTTP 200 with `bearerAuth` (`http`/`bearer`) and all three order/payment routes; public `GET /api/v1/events` returned HTTP 200.
- Domain framework-import scan and high-confidence raw-secret signature scan had no matches. Local `.env` is ignored and not tracked; `.env.example` remains the tracked template.
- `git diff --check` passed; Git emitted only the existing LF/CRLF conversion warnings.

### Phase 8 Error Report
1. Docker Engine was initially unavailable (`dockerDesktopLinuxEngine` pipe missing). Docker Desktop was started; Testcontainers and Compose then connected successfully.
2. Early E2E setup failed because `ObjectMapper` was not a Spring bean; the test now creates its own mapper.
3. The fixture tried `DRAFT -> ON_SALE`; Event is now published before the approved start-sale action.
4. Rabbit publisher-confirm testing initially failed with `Confirms not selected`; correlated confirms are explicitly configured for the E2E context.
5. The first E2E run stayed at `CREATING`: scanned consumers were suppressed by `@ConditionalOnBean` ordering. Explicit consumer configuration replaced that fragile condition, and bootstrap confirms the listener/job beans exist.
6. Bootstrap tests lacked queue-name placeholders; test queue defaults were added. The first full reactor run then reported 11 infrastructure context errors because listeners were enabled when their application use cases were absent. Making consumers opt-in by property fixed startup; worker integration fixtures now explicitly enable consumers and provide the expiry use case. That exposed three worker timeouts (consumers were not enabled in those fixtures) and one smoke assertion reading an earlier Rabbit message; explicit test enablement and queue purge fixed them.
7. The first E2E class run had two outbox assertion failures and one Redis-release recovery timeout. `PAYMENT_SUCCESS` is keyed by Payment aggregate, so the assertion now matches the event payload's order ID. RabbitMQ rejects the injected expiry-listener exception rather than automatically redelivering it, so the test now invokes and verifies the designed reconciliation repair.
8. The first complete reactor run exposed an unpurged Rabbit queue: the topology smoke test consumed an earlier test message. It now purges the queue before sending its own marker; the targeted tests and full reactor pass afterward.
9. A standalone `-pl infrastructure` Maven invocation could not resolve reactor-local modules; rerunning with `-am` passed. PowerShell brace syntax, `ConvertFrom-Json -Depth`, and `Invoke-WebRequest` caused command-shell errors; compatible commands and `curl.exe` verified the live endpoints.
10. The green suite still logs deliberate fault/duplicate cases: the injected Redis release and Rabbit publish failures, malformed expiry-message rejection, and duplicate-registration unique-key race. Each is asserted by its test; no test failure remains.

### Phase 8 Review
- `code-review` verdict: **APPROVED**; review identified that concurrency coverage asserted the final stock but not the minimum stock observed after each reserve. Added that invariant to the failure-injection test adapter and reran the full reactor; no actionable findings remain.
- Residual risk: payment-provider signatures and production Redis HA/cluster configuration remain intentionally deferred by the approved MVP scope.

### Phase 8 Completion
- Phase 8 and the overall feature plan were marked complete after the user's approval on 2026-10-07.

### Phase 7 Decisions
- Kept the confirmed three-endpoint REST contract: `POST /api/v1/orders`, `GET /api/v1/orders/{orderId}`, and `POST /api/v1/payments/callback`.
- Used the deterministic `LOCAL` payment adapter first. The Order worker opens one `PENDING` Payment in the same PostgreSQL transaction as Order creation, and polling exposes its provider/reference/status; no fourth initiation endpoint was added.
- Kept callback authorization provider-neutral for the MVP. The callback resolves the persisted Payment by `transactionRef` and validates the stored owner, provider, and amount; real provider signatures/authentication are deferred.
- Locked Payment, Order, and TicketType before applying the success transition. Only success changes `sold_quantity`, issues Tickets, and records `PAYMENT_SUCCESS`.
- Failure changes Payment/Order in PostgreSQL first, then releases Redis. Success marks the reservation intent completed and deletes only the short-lived hold, retaining the purchased user-limit counter.
- Outbox messages are durable and published with RabbitMQ confirms; a failed publish returns the row to retryable `PENDING` state. Future consumers must deduplicate at-least-once delivery by event id/business key.

### Phase 7 Verification
- Focused payment, Order-worker, polling, MockMvc, Redis, PostgreSQL, concurrency, RabbitMQ, and bootstrap API tests passed.
- Full reactor `./mvnw.cmd -q test`: 500 tests, 0 failures, 0 errors, 0 skipped.
- Docker rebuild/runtime passed: db, redis, rabbitmq, and app are up/healthy; live `/v3/api-docs` returned HTTP 200 with payment callback and bearer security metadata.
- `git diff --check` reported no whitespace errors; only Windows LF/CRLF conversion warnings.

### Phase 7 Error Report
1. An unquoted PowerShell Maven `-D` argument was parsed as a lifecycle phase; quoting it fixed the targeted test command.
2. Payment unit fixtures used UUIDv4 although the Order domain requires UUIDv7; the helper was corrected and the suite passed.
3. A replay fixture used a one-ticket amount for a two-ticket Order; it now uses the persisted Order total and passes.
4. The first full Outbox Rabbit assertion expected one row but the shared Testcontainer contained seven pending rows. The relay behavior was correct; the test now matches its own event id and full verification passes.
5. Review found no production caller for payment initiation; the Order worker now creates the local Payment transactionally and polling exposes the reference.
6. Review found initiation could return a pending attempt for an expired Order; terminal/inconsistent states are now rejected with regression coverage.
7. Only non-blocking Mockito/Byte Buddy, Hibernate, Spring Data Redis, negative-message, and concurrency duplicate-key warnings remain. No final test/runtime failure remains.

### Phase 7 Review
- `code-review` verdict: **APPROVED**; no actionable findings remain for transaction boundaries, authorization, idempotency, Redis cleanup, Outbox retry, API metadata, or tests.

### Phase 7 Next immediate action
Completed: the user explicitly approved starting Phase 8 on 2026-09-28.

### Phase 6 Decisions
- Kept RabbitMQ as the expiry signal: the relay publishes a versioned expiry message with per-message TTL, the delay queue dead-letters to the expiry queue, and the listener rechecks the persisted deadline.
- Split expiry into a transactional PostgreSQL transition and a post-commit Redis release. Only `PENDING_PAYMENT -> EXPIRED` authorizes normal release; `PAID` is never released.
- Retained Redis reservation intents beyond the short hold TTL and added `enqueuedAt`, `COMPLETED`, bounded grace, and reconciliation batch settings so missing Orders and release failures remain repairable.
- Reused the relay claim/lease mechanism for reconciliation. The reconciliation job is internal/scheduled only; no unrestricted diagnostic endpoint was added.
- Added a terminal-state guard so stale relay retries cannot move `COMPLETED` intents back to `PENDING`; removed an unused stock-cache dependency from the reconciliation job wiring.

### Phase 6 Verification
- Focused post-review verification: `RedisReservationAdapterIntegrationTest` and `ReservationReconciliationJobTest` passed, 12 tests total.
- Full reactor: `./mvnw.cmd -pl bootstrap -am '-Dsurefire.failIfNoSpecifiedTests=false' test` — pass, 485 tests, 0 failures, 0 errors, 0 skipped.
- Infrastructure suite: 73 tests, including real PostgreSQL/Redis/RabbitMQ expiry and topology tests.
- Real TTL/DLX tests passed for unpaid expiry/release-once and payment-winning/no-release paths.
- Docker rebuild and runtime check passed; all four services healthy and live `/v3/api-docs` returned HTTP 200 after startup.
- `git diff --check` — no whitespace errors; only LF/CRLF conversion warnings.

### Phase 6 Error Report
1. The first guessed phase-document filename did not exist; the actual `phase-06-expiry-and-reconciliation.md` was used.
2. `OrderExpiryPublisher` initially failed Spring startup with `BeanCreationException: No default constructor found`; explicit `@Autowired` constructor wiring fixed it.
3. `ReservationReconciliationJob` had the same constructor-wiring failure; explicit `@Autowired` wiring fixed it.
4. The payment-winning TTL assertion ran before the expiry consumer finished and observed `ORDER_CREATED` instead of `COMPLETED`; it now waits for eventual completion and still verifies no release/expiry event.
5. Review found a stale retry could reschedule `COMPLETED`; the Lua guard and regression test now prevent that transition.
6. The first live OpenAPI request after app recreation raced readiness and the connection closed; a readiness retry returned HTTP 200.
7. Expected non-blocking warnings remained: Mockito/Byte Buddy, Hibernate schema/open-in-view, Spring Data Redis repository-assignment messages, malformed-message negative-test logs, and duplicate-key logs from passing concurrency tests. No final test or runtime failure remains.

### Phase 6 Review
- `code-review` verdict: **APPROVED**; no actionable findings remain.

### Phase 6 Next immediate action
Wait for explicit user approval before starting Phase 7: local/test payment initiation and callback, ticket issuance, and Outbox publishing.

### Phase 5 Decisions
- Kept the selected Redis Hash + Sorted Set intent design and added a scheduled relay with a short claim lease, publisher confirms, and bounded retry backoff.
- Used a version-1 RabbitMQ order-create message carrying the reservation snapshot, including the preassigned `orderId`, price, and expiry timestamps.
- Made the Order worker transactional and idempotent by `orderId`; it creates exactly one Order, one OrderItem, and one `ORDER_CREATED` OutboxEvent without changing `sold_quantity`.
- Chose manual Rabbit acknowledgement after the worker transaction returns, with duplicate deliveries treated as successful no-ops.
- Exposed authenticated `POST /api/v1/orders` returning `202 CREATING` and owner-checked `GET /api/v1/orders/{orderId}` for polling.

### Phase 5 Verification
- Full reactor: `./mvnw.cmd -pl bootstrap -am '-Dsurefire.failIfNoSpecifiedTests=false' test` — pass, 463 tests, 0 failures, 0 errors, 0 skipped.
- Infrastructure suite: `./mvnw.cmd -pl infrastructure -am '-Dsurefire.failIfNoSpecifiedTests=false' test` — pass, 65 tests.
- Redis/Rabbit/PostgreSQL flow: targeted topology, adapter, and order-flow integration tests — pass, 12 tests.
- Docker runtime: `docker compose up -d --build` — pass; PostgreSQL, Redis, RabbitMQ, and app healthy.
- Live OpenAPI: `GET http://localhost:8080/v3/api-docs` — `200`; both Order endpoints expose `bearerAuth`.
- `git diff --check` — pass; only Git line-ending conversion warnings were reported.

### Phase 5 Error Report
1. The initial phase filename and a few source paths were guessed incorrectly; the existing names were located and used without changing scope.
2. A Maven module-only dependency-tree command failed because upstream reactor modules were omitted; rerunning with `-am` fixed it.
3. An early parallel test attempt caused class-loading races between shared Maven target directories; tests were rerun sequentially.
4. The first integration context missed messaging queue properties; the test application configuration was completed.
5. The relay initially lacked an explicit constructor injection marker, then failed against a smoke context with messaging disabled; `@Autowired` and combined enable conditions fixed both cases.
6. An initial Awaitility assertion read an empty polling result too early; it was changed to assert presence before inspecting the value.
7. The Redis TTL test was affected by the relay scheduler running in an adapter-only context; relay is now disabled by default in that test configuration and enabled only for the order-flow integration test.
8. The first live OpenAPI request arrived during application startup and the connection closed; a readiness retry succeeded with HTTP 200. Startup also emits expected Hibernate schema warnings for missing constraints and the standard `open-in-view` warning.
9. Expected non-blocking test/runtime warnings remain: Mockito/Byte Buddy dynamic-agent warnings, Hibernate SQL logs, and malformed-message rejection logs in the negative listener test. No test failure remains.

### Phase 5 Review
- `code-review` verdict: **APPROVED**; no actionable findings remain for the relay, worker, API, configuration, or tests.
- Residual scope is intentionally deferred to Phase 6: TTL/DLX expiry handling, idempotent stock release, and reconciliation for stuck intents/messages.

### Phase 5 Next immediate action (historical)
Wait for explicit user approval before starting Phase 6: TTL/DLX expiry, idempotent release, and reconciliation.

### Phase 4 Decisions
- Added infrastructure-only JPA entities for Order/OrderItem, Payment, Ticket, and OutboxEvent; domain objects remain persistence-agnostic.
- Enforced database uniqueness for order code, payment order/transaction references, and ticket code, plus cascading OrderItem deletion.
- Used `PESSIMISTIC_WRITE` for state-transition reads and `Propagation.MANDATORY` so payment/expiry callers must hold the lock in one transaction.
- Used `FOR UPDATE SKIP LOCKED` for concurrent Outbox publishers; the caller must keep the transaction open through publish and status update.
- Accounted for PostgreSQL microsecond timestamp precision in integration assertions.

### Phase 4 Verification
- `./mvnw.cmd -pl infrastructure -am test` — pass: 227 domain + 75 application + 59 infrastructure tests.
- `./mvnw.cmd -pl bootstrap -am test '-Dsurefire.failIfNoSpecifiedTests=false'` — pass: 227 domain + 75 application + 59 infrastructure + 80 presentation + 9 bootstrap tests.
- `git diff --check` — pass; only LF/CRLF conversion warnings were reported.

### Phase 4 Error Report
1. The first PowerShell targeted-test command failed before Maven because the comma-separated `-Dtest` value was not quoted. Quoting the argument fixed it.
2. Three timestamp equality assertions failed because PostgreSQL `timestamp(6)` truncates/rounds to microseconds while `Instant.now()` can contain nanoseconds. Assertions now use a two-microsecond tolerance; tests pass.
3. An intermediate AssertJ fix did not compile because `Instant` assertions require `TemporalOffset`, not `Duration`. Replaced it with `within(2, ChronoUnit.MICROS)`; tests pass.

### Phase 4 Review
- `code-review` verdict: **APPROVED**; no actionable findings remain.
- Later phases still need to wire the RabbitMQ relay/worker, expiry/payment orchestration, callback, ticket issuance, and final E2E/failure tests.

### Phase 4 Next immediate action (historical)
Wait for explicit user approval before starting Phase 5: RabbitMQ relay, Order worker, and reservation/status REST flow.

### Phase 3 Decisions (historical)
- Added `ReservationIntentStore` as the adapter-neutral relay/reconciliation port for due reads, claim leases, retry scheduling, and terminal state transitions.
- Used Redis Hashes as the intent/hold records and `reservation:pending` as the retry index; timestamps and amounts use explicit String serialization, with epoch-millis only for Sorted Set scores.
- Made the reserve Lua script create stock/user-counter changes, the short hold, the long-lived intent, and the pending index in one atomic execution.
- Made release use the intent's `holdState` as its idempotency marker, so it still restores inventory after the short hold Hash expires and never restores twice.
- Kept cache warming in the already-approved `StartSaleUseCase`; `warmUp` uses set-if-absent and refuses to overwrite live stock.

### Phase 3 Verification (historical)
- `./mvnw.cmd -pl infrastructure -am -Dtest=RedisReservationAdapterIntegrationTest '-Dsurefire.failIfNoSpecifiedTests=false' test` — pass, 9 Redis integration tests; Docker-backed Redis 7, RabbitMQ 4, and PostgreSQL 16 started by Testcontainers.
- `./mvnw.cmd -pl infrastructure -am test` — pass, 227 domain + 75 application + 39 infrastructure tests.
- `./mvnw.cmd -pl bootstrap -am test '-Dsurefire.failIfNoSpecifiedTests=false'` — pass, 227 domain + 75 application + 39 infrastructure + 80 presentation + 9 bootstrap tests.
- `git diff --check` — pass before the final checklist-only edits; no code whitespace errors were introduced afterward.

### Phase 3 Review (historical)
- `code-review` verdict: APPROVED. No actionable findings remain in the Phase 3 adapter, scripts, port, or tests.
- Residual risk is intentionally deferred to later phases: Rabbit publisher confirms/worker idempotency, PostgreSQL Order persistence, expiry/payment race, API wiring, and production Redis HA/cluster slot behavior.

### Phase 3 Next immediate action (historical)
Wait for explicit user approval before starting Phase 4: PostgreSQL Order, Payment, Ticket, and Outbox persistence.

### Phase 2 Verification (historical)
- `./mvnw.cmd -pl domain,application -am test` — pass, 227 domain + 75 application tests.
- `./mvnw.cmd -pl bootstrap -am test '-Dsurefire.failIfNoSpecifiedTests=false'` — pass, 227 domain + 75 application + 30 infrastructure + 80 presentation + 9 bootstrap tests.
- `git diff --check` — pass.

### Phase 2 Review (historical)
- `code-review` verdict: APPROVED. No actionable findings remain in the Phase 2 diff.
- Residual risk is intentionally deferred to later phases: Redis Lua correctness, relay retry/claim behavior, Order persistence/idempotency, expiry/payment race, API wiring, and concurrency invariants are not implemented or tested yet.

### Phase 2 Next immediate action (historical)
Wait for explicit user approval before starting Phase 3: Redis Lua reservation, Hash/Sorted Set intent storage, release script, and cache warming adapter.

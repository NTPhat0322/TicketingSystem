# Phase 2: Domain/application reservation contracts and Order status model

## P1 Stories Satisfied

- Starts the atomic reservation story.
- Defines the `202 CREATING` response and polling result contract.

## Affected Areas

- `domain/src/main/java/com/tienphat/domain/model/Order.java`
- `domain/src/main/java/com/tienphat/domain/port/StockCachePort.java`
- new domain/application reservation request/result records and ports
- `application/src/main/java/com/tienphat/application/reservation/`
- `application/src/main/java/com/tienphat/application/order/`
- unit tests under `domain/src/test` and `application/src/test`

## Requirements

1. Let the application supply the pre-generated `orderId` to the Order factory/reconstitution path. Preserve the UUIDv7 identity rule and existing domain validation.
2. Define an adapter-neutral atomic reservation contract that carries `orderId`, `userId`, `eventId`, `ticketTypeId`, quantity, unit price snapshot, `maxPerUser`, and `expiresAt`.
3. Evolve `StockCachePort` or introduce a narrowly named companion port so the Redis adapter can atomically create both inventory state and pending intent without exposing Redis types to application code.
4. Add `ReserveTicketCommand`, `ReserveTicketResult`, and `ReserveTicketUseCase`. It must load Event/TicketType, require the event to be sellable, generate the order identity, and call the atomic reservation port.
5. Add `GetOrderStatusUseCase` and a result that can represent `CREATING` before the Order row exists and persisted Order statuses afterward.
6. Keep one TicketType per reservation in the first implementation; reject non-positive quantity and invalid ownership/request identity.
7. Define domain/application exceptions for sold out, per-user limit, missing/unwarmed stock, unavailable event/ticket type, and missing/foreign Order status access.
8. Add `StartSaleUseCase`: load the Event and all TicketTypes, warm every Redis stock key, then call `Event.startSale()` and persist the Event. Do not add a scheduled `saleStartTime` trigger in this phase.

## Implementation Steps

1. Add the explicit-id Order factory overload and update domain tests for generated and supplied IDs.
2. Define immutable reservation data records and intent-state values without Redis/Jackson dependencies.
3. Update `StockCachePort` documentation/contracts to reflect that the atomic reserve operation also records the pending intent; keep `release` and `warmUp` semantics explicit.
4. Implement `ReserveTicketUseCase` as a plain application service with `@Transactional` only for PostgreSQL reads; Redis reservation remains the atomic external operation.
5. Implement the polling result contract: query `OrderRepository` first; if absent, query the intent-status port; enforce caller ownership from `AuthorizationContext`.
6. Implement `StartSaleUseCase` so warming all TicketTypes happens before `Event.startSale()`. A warm-up failure must prevent the state transition.
7. Add unit tests for event/ticket availability, order identity propagation, price/expiry snapshot, Redis failure propagation, `CREATING` versus persisted status mapping, and warm-before-start ordering.

## Invariants

- `orderId` is generated exactly once before the Redis call and reused everywhere.
- No application code performs a PostgreSQL Order insert in the reservation request.
- `sold_quantity` is not changed by reservation.
- The unit price used later by `OrderItem` comes from the reservation snapshot, not a later TicketType reload.
- A client cannot poll another user's Order or intent.

## Success Criteria

- Application unit tests prove a successful reservation command emits one pre-generated `orderId` and a `CREATING` result.
- Failure results do not call any repository save and do not mutate domain state.
- Domain tests still pass and no framework/persistence imports enter `domain`.
- A missing intent and missing Order are represented as a controlled not-found/expired result, not a `NullPointerException`.

## Test Strategy

- Domain unit tests for Order identity and lifecycle.
- Mockito unit tests for `ReserveTicketUseCase` and `GetOrderStatusUseCase`.
- Explicit tests that verify `StockCachePort` is called with the same `orderId`, snapshot price, quantity, and expiry used in the result.

## Rollback Notes

If the port shape proves wrong, keep the application records and revert only the adapter boundary. Do not reintroduce an application-side stock decrement or a second PostgreSQL reservation path.

## Risks

- The existing `StockCachePort` is named as a stock port but now spans the atomic reservation boundary. If that becomes too broad during implementation, split the intent read/update operations into an application-owned port while keeping the single Redis Lua reserve operation intact.

## Checklist

- [x] Add explicit UUIDv7 Order identity and preserve captured reservation timestamps.
- [x] Define adapter-neutral reservation request, intent, intent state, and status-port contracts.
- [x] Evolve `StockCachePort` so reserve carries the full snapshot and release has an `orderId` idempotency key.
- [x] Implement reservation, polling, and explicit start-sale application use cases.
- [x] Add business exceptions for sale availability, stock warm-up, user limits, sold-out inventory, and missing orders.
- [x] Add domain/application unit tests for identity propagation, snapshots, ownership, CREATING mapping, and warm-before-start ordering.

## Verification Evidence

- `./mvnw.cmd -pl domain,application -am test` — pass: 227 domain tests and 75 application tests.
- `./mvnw.cmd -pl bootstrap -am test '-Dsurefire.failIfNoSpecifiedTests=false'` — pass: 400 tests across all modules.
- `git diff --check` — pass.
- Domain/application source contains no Redis, RabbitMQ, Jackson, or persistence imports.

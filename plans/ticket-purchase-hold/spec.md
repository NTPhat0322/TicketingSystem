# Spec: Ticket purchase and asynchronous reservation

**Date:** 2026-09-25
**Status:** Ready

---

## Problem Statement

The ticketing system must reserve limited ticket inventory safely when many authenticated users purchase concurrently. Redis must protect the sale-time stock from overselling, while RabbitMQ moves Order creation away from the request thread and still allows the client to track the Order through a temporary `CREATING` state.

---

## User Stories

- **[P1]** As an authenticated customer, I want to reserve a TicketType so that my requested tickets are held without overselling the available stock.
  Accepted when: the Redis Lua operation atomically checks stock and `max_per_user`, decrements stock, creates the hold and pending intent, and rejects the request when either constraint fails.

- **[P1]** As a customer, I want an immediate reservation identifier so that I can track an Order that is still being created.
  Accepted when: the API returns `202 Accepted`, a generated `orderId`, and status `CREATING` after the Redis operation succeeds; the response does not claim that payment can start before the Order exists.

- **[P1]** As a customer, I want to poll my reservation so that I know when it is ready for payment.
  Accepted when: the status changes from `CREATING` to `PENDING_PAYMENT`, and later to `PAID` or `EXPIRED` according to the Order lifecycle.

- **[P1]** As the platform, I want to retry failed Order-create messages so that a broker or application interruption does not silently lose a successful Redis reservation.
  Accepted when: a pending Redis intent remains discoverable by the relay, failed publishes are rescheduled, and the relay removes the pending Sorted Set entry only after RabbitMQ publisher confirmation.

- **[P1]** As the platform, I want duplicate RabbitMQ deliveries to be safe so that at-least-once delivery cannot create duplicate Orders.
  Accepted when: repeated messages with the same `orderId` result in one Order and one set of OrderItems, with the duplicate delivery acknowledged safely.

- **[P1]** As the platform, I want unpaid holds to expire so that reserved inventory becomes available again.
  Accepted when: TTL + DLX sends an expiry message, the expiry worker changes only `PENDING_PAYMENT` Orders to `EXPIRED`, and Redis stock/user-limit counters are restored exactly once.

- **[P1]** As the platform, I want successful payment to finalize a reservation so that the customer receives tickets and inventory accounting is correct.
  Accepted when: a successful payment transaction atomically changes the Payment and Order, increments `sold_quantity` exactly once, creates the Tickets, and records the relevant `OutboxEvent`.

- **[P2]** As a customer, I want to cancel an unpaid Order manually so that I can release my hold before it expires.
  _(out of scope for the first implementation; use TTL expiry first)_

- **[P3]** _(out of scope)_ Use Redis Stream consumer groups for reservation intent delivery.

---

## Functional Requirements

1. **FR-01:** Reservation requires an authenticated user. The application generates `orderId` before calling Redis. The MVP accepts one `TicketType` and a positive quantity per reservation request.
2. **FR-02:** The Redis Lua script must atomically validate the stock key and the per-user limit, decrement `stock:ticket_type:{ticketTypeId}`, increment `user:limit:{ticketTypeId}:{userId}`, create the hold record, create the reservation intent, and add `orderId` to `reservation:pending`.
3. **FR-03:** A successful reservation returns `202 Accepted` with at least `orderId`, `status = CREATING`, and the expected expiration time. A stock or per-user-limit failure returns a conflict-style business error and must not leave a partial Redis decrement.
4. **FR-04:** The reservation intent is stored as `reservation:intent:{orderId}` with the identifiers, quantity, timestamps, state, retry count, and next retry time. `reservation:pending` is a Sorted Set whose member is `orderId` and whose score is `nextRetryAt`.
5. **FR-05:** A relay reads due members from `reservation:pending`, loads the matching Hash, and publishes an order-create command to a durable RabbitMQ exchange/queue. It removes the Sorted Set member only after publisher confirmation; failures reschedule the member with retry metadata.
6. **FR-06:** The Order worker consumes the command in a database transaction and creates the Order plus OrderItems with `PENDING_PAYMENT`. It must be idempotent by `orderId`; duplicate commands must not create duplicate rows or change `ticket_types.sold_quantity`.
7. **FR-07:** A status endpoint can return `CREATING` while the Order row is not yet present, then returns the persisted Order status once the worker creates it. The caller may access only their own Order unless an existing administrative rule explicitly allows otherwise.
8. **FR-08:** The hold expiry path uses RabbitMQ TTL + Dead Letter Exchange. The expiry worker re-checks the Order and changes it from `PENDING_PAYMENT` to `EXPIRED` conditionally. It must not expire a `PAID` Order and must not release stock when the conditional transition did not occur.
9. **FR-09:** Redis release is performed by an idempotent Lua operation that restores stock and the user-limit counter once and marks the reservation as released. Redis key expiration alone is not considered a release operation.
10. **FR-10:** A reconciliation job periodically finds pending intents that missed publication, enqueued intents whose Order was not created within the retry window, and expired/stale reservations whose Redis release is incomplete. It retries or releases according to the reservation state without double-counting stock.
11. **FR-11:** Payment confirmation is idempotent by the provider transaction reference. Only a successful payment may change the Order to `PAID`, increment `sold_quantity`, create Tickets, and record `OutboxEvent(PAYMENT_SUCCESS)`.
12. **FR-12:** Existing `OutboxEvent` behavior remains PostgreSQL-side. `ORDER_CREATED`, `ORDER_EXPIRED`, and `PAYMENT_SUCCESS` are recorded in the same database transaction as the business state change that produced them; the initial Redis reservation intent is not represented as an OutboxEvent.
13. **FR-13:** The system must fail closed when Redis is unavailable or the required stock key is missing. It must not fall back to a second reservation algorithm through PostgreSQL.
14. **FR-14:** The MVP's explicit `StartSaleUseCase` must warm every TicketType stock key for the Event before calling `Event.startSale()`. A scheduled job based on `saleStartTime` is deferred; reservation must still fail closed if the stock key is not warmed.

---

## Non-Functional Requirements

- **Performance:** the reservation endpoint should complete the Redis reservation and return its `202` response with p95 latency below 300 ms under the project's initial concurrency test; Order creation and payment finalization are asynchronous.
- **Consistency:** accepted reservations must never make the Redis stock value negative; each `orderId` may produce at most one Order and one successful stock release.
- **Reliability:** RabbitMQ messages and queues are durable; publisher confirms are enabled; the reconciliation job runs at a configurable fixed interval and survives application restarts by reading Redis state.
- **Security:** only an authenticated user can reserve; `userId` comes from the verified access token, not from an untrusted request field; users cannot poll another user's Order.
- **Observability:** reservation, publish retry, worker duplicate, expiry, release, and reconciliation actions must include `orderId` and `ticketTypeId` in structured logs or equivalent diagnostics.

---

## Success Criteria

- [ ] A concurrency test with more reservation attempts than available stock accepts no more than the warmed Redis stock quantity and never produces a negative stock value.
- [ ] A Redis hold plus application crash before RabbitMQ publication is recovered by the relay/reconciliation path, or is released after the configured grace period without permanently leaking stock.
- [ ] Replaying the same Order-create message multiple times creates exactly one Order and its OrderItems.
- [ ] An expiry message for a `PENDING_PAYMENT` Order restores stock once; replaying the expiry message does not restore it twice.
- [ ] An expiry message racing with successful payment leaves a `PAID` Order intact and does not release its stock.
- [ ] Duplicate payment callbacks with the same transaction reference increment `sold_quantity` and create Tickets only once.
- [ ] The client can observe `CREATING` before the worker creates the Order and `PENDING_PAYMENT` after creation through the status endpoint.
- [ ] The existing test suite and the new Redis/Rabbit/PostgreSQL integration tests pass in the Docker-backed environment.

---

## Out of Scope

- Redis Stream consumer groups; the first implementation uses Redis Hash + Sorted Set.
- A separate reservation domain aggregate or PostgreSQL reservation-intent table.
- Manual Order cancellation, refunds, chargebacks, and returning tickets to inventory after payment.
- Multiple TicketTypes in one reservation/cart.
- Ticket check-in and QR scanning.
- A specific external payment provider integration; the application uses a provider callback port/contract.
- Redis Sentinel/Cluster topology and production capacity tuning; local Docker support is sufficient for the first implementation.
- Email/notification consumers beyond recording the existing OutboxEvent.
- Splitting the monolith into microservices.

---

## Assumptions

- Redis is the live inventory source of truth during the sale, and missing/unavailable Redis causes a fail-closed response.
- `orderId` is generated by the application before reservation and is the idempotency key for Order creation.
- The hold duration comes from the TicketType configuration and the reservation intent is retained longer than the short-lived hold metadata so reconciliation can still release stock.
- Inventory is warmed in Redis by an explicit `StartSaleUseCase` before an Event becomes purchasable. A future scheduled job may invoke the same use case based on `saleStartTime`.
- The first payment flow uses a deterministic local/test payment adapter; VNPAY/MOMO/Stripe integration is deferred.
- The first REST contract is `POST /api/v1/orders`, `GET /api/v1/orders/{orderId}`, and `POST /api/v1/payments/callback`.
- The current domain rules remain: `sold_quantity` changes only on successful payment, and `OutboxEvent` is written with the PostgreSQL business transaction.
- RabbitMQ TTL + DLX is the chosen expiry mechanism for the first implementation.

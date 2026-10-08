# Phase 7: Payment initiation/callback, ticket issuance, and Outbox publisher

## P1 Stories Satisfied

- Successful payment finalizes the Order.
- `sold_quantity` changes only on successful payment.
- Tickets and `PAYMENT_SUCCESS` OutboxEvent are created idempotently.

## Affected Areas

- `application/src/main/java/com/tienphat/application/payment/`
- `application/src/main/java/com/tienphat/application/ticket/`
- `infrastructure/src/main/java/com/tienphat/infrastructure/messaging/` and `outbox/`
- `presentation/src/main/java/com/tienphat/presentation/payment/`
- `presentation/src/main/java/com/tienphat/presentation/config/UseCaseConfig.java`
- payment/outbox integration tests

## Requirements

1. Define a provider-neutral `PaymentGatewayPort` and local/test adapter. The exact provider endpoint/signature is outside this plan.
2. Implement payment initiation for an existing `PENDING_PAYMENT` Order, enforcing owner and amount snapshot. Persist one Payment attempt with unique `transactionRef`.
3. Implement the callback use case keyed by `transactionRef`. It must lock/guard the Payment and related Order, ignore a replay after success, reject invalid state, and keep the callback response stable.
4. In one PostgreSQL transaction on successful payment:
   - mark Payment `SUCCESS`;
   - pay the Order;
   - lock/load TicketType and call `confirmSale(quantity)`;
   - issue one Ticket per quantity with unique codes;
   - record `OutboxEvent(PAYMENT_SUCCESS)`.
5. Ensure no expiry path can release the sale after Payment becomes `PAID`. Clean up hold/intent metadata without decrementing the user's purchased-limit counter.
6. Implement Outbox persistence/publisher: poll pending events safely, publish persistent integration messages with confirms, mark `PUBLISHED` or retry `FAILED → PENDING`. Consumers remain idempotent.
7. Add the provider-neutral `POST /api/v1/payments/callback` endpoint and use the local/test adapter for the first implementation. Real provider endpoints and signature verification are deferred.

## Implementation Steps

1. [x] Add payment/ticket application commands/results and map the existing domain factories/mutators.
2. [x] Add payment gateway port and deterministic test adapter.
3. [x] Implement the transactional confirmation use case with row locks/conditional state checks.
4. [x] Add ticket-code generation strategy and uniqueness tests.
5. [x] Wire OutboxEvent repository adapter, publisher scheduler, routing, and failure retry.
6. [x] Add MockMvc and full-context tests for successful callback, duplicate callback, failed callback, wrong owner, and expired Order.

## Completion Checklist

- [x] Order creation automatically opens one local `PENDING` Payment in the same PostgreSQL transaction, and polling exposes its provider/reference/status without adding a fourth endpoint.
- [x] Callback locks Payment, Order, and TicketType in a stable order; success marks `PAID`, confirms sale, issues one Ticket per quantity, and records `PAYMENT_SUCCESS`.
- [x] Duplicate success callbacks are no-ops; failed callbacks release Redis after commit; expired/terminal Orders cannot be paid or re-opened.
- [x] Redis completion removes the short-lived hold while retaining the purchased user-limit counter.
- [x] Outbox publishing uses durable RabbitMQ messages and publisher confirms; failures return rows to retryable `PENDING` state.
- [x] Callback, transaction, Redis, RabbitMQ, PostgreSQL, API, concurrency, and Docker-backed tests are green.

## Invariants

- `sold_quantity` is incremented exactly once per successful payment and never during reserve/order-create/expiry.
- Payment replay cannot create duplicate Tickets or duplicate Outbox business effects.
- An Order cannot be paid after it is `EXPIRED`, `CANCELLED`, or `FAILED`.
- Raw provider payloads/signatures are not trusted for amount/ownership; the callback resolves the persisted Payment by transaction reference.
- Outbox recording is in the same DB transaction as the payment/ticket state change.

## Success Criteria

- One successful callback creates the expected Payment/Order/Ticket/sold-quantity state and one `PAYMENT_SUCCESS` event.
- Replaying the callback returns an idempotent success/no-op response and does not add tickets or sold quantity.
- Concurrent callbacks for the same transaction reference result in one winner.
- An expired Order cannot be paid.
- A pending Outbox row is published and marked `PUBLISHED`; a simulated broker failure returns it to a retryable state.

## Test Strategy

- Application unit tests for payment state and orchestration.
- PostgreSQL integration tests for locks, unique constraints, and transaction rollback.
- RabbitMQ integration test for Outbox publish confirmation/retry.
- Full API test with the local payment adapter.

## Rollback Notes

If the external provider contract is not ready, keep the local adapter and callback contract behind configuration. Do not bypass Payment persistence or directly mark Orders paid from a controller.

## Risks

- Ticket code generation and duplicate callback handling must be transactionally aligned; a unique ticket code alone is not enough to prove exactly-once issuance.
- Outbox has at-least-once delivery. Any future consumer must deduplicate by event id or business key.

## Verification Evidence

- Focused application tests for payment initiation, callback replay/failure/ownership/expiry, Order-worker initiation, and polling payment details passed.
- PostgreSQL integration tests passed for successful callback replay and two concurrent callbacks; only one sale/ticket effect was committed.
- Redis integration tests passed for completed-hold cleanup with the purchased user-limit counter retained.
- RabbitMQ integration tests passed for publisher confirms, `PUBLISHED` transition, and failure retry back to `PENDING`.
- Full reactor `./mvnw.cmd -q test`: 500 tests passed, 0 failures, 0 errors, 0 skipped.
- `docker compose up -d --build`: PostgreSQL, Redis, RabbitMQ, and app started; all containers reported healthy/up.
- Live `GET http://localhost:8080/v3/api-docs`: HTTP 200; callback path, order polling path, and `bearerAuth` metadata were present.
- `git diff --check`: no whitespace errors; only expected Windows LF/CRLF conversion warnings.

## Phase Error Report

1. The first targeted Maven command was passed through PowerShell without quoting `-Dsurefire.failIfNoSpecifiedTests=false`; Maven interpreted `.failIfNoSpecifiedTests=false` as a lifecycle phase. Quoting the `-D` arguments fixed the command.
2. The first payment unit run used random UUIDv4 fixtures, but `Order.create(...)` correctly requires UUIDv7 and raised `InvalidOrderDataException`. The test helper now uses the valid Order factory path; the targeted suite passed.
3. The payment replay unit fixture stored an amount for one ticket while the Order contained two tickets, so callback matching rejected the replay. The fixture now uses `order.getTotalAmount()`; the targeted suite passed.
4. The first full reactor run expected the Outbox Rabbit relay to publish exactly one row but observed seven pending rows from the shared PostgreSQL Testcontainer. The relay was correctly draining its batch; the integration assertion now waits for the test event by message id, and the targeted plus full suites passed.
5. Review found that the payment-initiation bean had no production caller, leaving the three-endpoint flow without a Payment reference. The Order worker now opens the local Payment transactionally and the polling response exposes its reference; Rabbit/PostgreSQL integration coverage passed.
6. Review found that a pending Payment could be returned for an already expired Order. Initiation now rejects terminal/inconsistent Order-Payment states, with a regression test; the full reactor passed.
7. Non-blocking warnings remained: Mockito/Byte Buddy dynamic-agent notices, Hibernate missing-constraint and `open-in-view` warnings, Spring Data Redis repository-assignment messages, malformed-message negative-test logs, and expected duplicate-key logs from concurrency tests. No final test or runtime failure remains.

## Review

`code-review` verdict: **APPROVED**. No actionable findings remain for transaction boundaries, authorization, idempotency, Redis cleanup, Outbox retry, API contract, or test coverage. Real provider signatures and provider-specific callback authentication remain intentionally deferred by the MVP decision.

## Next Gate

Phase 7 is complete. Wait for explicit approval before starting Phase 8: full Docker-backed E2E, concurrency, and failure verification.

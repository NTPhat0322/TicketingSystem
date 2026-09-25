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

1. Add payment/ticket application commands/results and map the existing domain factories/mutators.
2. Add payment gateway port and deterministic test adapter.
3. Implement the transactional confirmation use case with row locks/conditional state checks.
4. Add ticket-code generation strategy and uniqueness tests.
5. Wire OutboxEvent repository adapter, publisher scheduler, routing, and failure retry.
6. Add MockMvc and full-context tests for successful callback, duplicate callback, failed callback, wrong owner, and expired Order.

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

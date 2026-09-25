# Phase 4: PostgreSQL Order, Payment, Ticket, and Outbox persistence

## P1 Stories Satisfied

No HTTP story is completed alone, but this phase supplies the durable records required by the worker and payment flow.

## Affected Areas

- `infrastructure/src/main/java/com/tienphat/infrastructure/order/`
- `infrastructure/src/main/java/com/tienphat/infrastructure/payment/`
- `infrastructure/src/main/java/com/tienphat/infrastructure/ticket/`
- `infrastructure/src/main/java/com/tienphat/infrastructure/outbox/`
- `infrastructure/pom.xml` only if transaction/test support is missing
- PostgreSQL integration tests

## Requirements

1. Implement JPA entities, repositories, mappers, and domain repository adapters for Order/OrderItem, Payment, Ticket, and OutboxEvent using the existing Event/TicketType pattern.
2. Preserve domain purity: JPA annotations stay in infrastructure entities; entity-to-domain mapping calls `reconstitute(...)` through MapStruct factories.
3. Add database uniqueness and relationship constraints:
   - `orders.id` primary key and `order_code` unique;
   - `order_items.order_id` foreign key/cascade;
   - `payments.order_id` unique and `transaction_ref` unique;
   - `tickets.ticket_code` unique;
   - Outbox status/type/aggregate columns and a pending lookup index where supported.
4. Add lock-aware repository methods for payment callback and expiry races. Use pessimistic row locking or conditional updates; do not rely on an unconstrained read-modify-write sequence.
5. Implement `OutboxEventRepository.findAllPending` with a safe multi-publisher strategy (`FOR UPDATE SKIP LOCKED` or an equivalent claim operation) at the infrastructure boundary.

## Implementation Steps

1. Create entities and schema mappings, starting with Order/OrderItem because the worker needs them first.
2. Create MapStruct persistence mappers with explicit factories for all private domain constructors and assert every field, including timestamps, status, amount, and versioned TicketType reads.
3. Create Spring Data repositories and domain adapters.
4. Add lock-aware methods for Order, Payment, and TicketType access used by later phases.
5. Add integration tests against PostgreSQL Testcontainers for constraints, cascade, round trips, locks, and Outbox pending queries.
6. Verify `ddl-auto: update` produces all required tables/constraints in the existing local setup; do not add a migration framework in this feature.

## Invariants

- Order creation never updates `ticket_types.sold_quantity`.
- Payment uniqueness is enforced by both the domain/application guard and database constraints.
- A Ticket exists only after payment success.
- An OutboxEvent is persisted in the same PostgreSQL transaction as the business state that caused it.
- Domain classes remain free of `jakarta.persistence`, Spring Data, Jackson, and AMQP imports.

## Success Criteria

- Each adapter has a real Testcontainers PostgreSQL round-trip test with explicit field assertions, not ID-only equality.
- Duplicate `orderId`, duplicate `orderCode`, duplicate `transactionRef`, and duplicate `ticketCode` are rejected/handled as specified.
- Lock-aware repository tests demonstrate that concurrent payment/expiry paths cannot both win the same state transition.
- `OutboxEventRepository.findAllPending` does not allow two publisher transactions to claim the same row.

## Test Strategy

- Mapper unit tests for every aggregate.
- Repository integration tests against PostgreSQL 16.
- Constraint and concurrent state-transition tests using separate transactions/threads where practical.

## Rollback Notes

If schema generation or mappings fail, keep the new entities/adapters isolated from existing tables and revert only the feature mappings. Do not modify existing User/Event/TicketType mappings except adding the lock/query methods required by the new flow.

## Risks

- The project is code-first with `ddl-auto: update`; a future migration system may need to replace these mappings. Record generated schema assumptions in tests rather than hiding them in manual SQL.
- Assigned UUID IDs can cause Spring Data to choose merge semantics; functional correctness is the priority for this phase, not premature insert-path optimization.


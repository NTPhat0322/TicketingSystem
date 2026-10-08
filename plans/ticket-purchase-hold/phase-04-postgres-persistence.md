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

## Checklist

- [x] Add JPA entities for `Order`/`OrderItem`, `Payment`, `Ticket`, and `OutboxEvent`.
- [x] Add MapStruct persistence mappers that reconstruct domain objects through `reconstitute(...)` and preserve `Money`, status, timestamps, and child relationships.
- [x] Add repository adapters and constraints for unique order codes, payment order/transaction references, ticket codes, and cascading order items.
- [x] Add pessimistic lock methods for Order, Payment, and TicketType with an explicit `Propagation.MANDATORY` transaction contract.
- [x] Add Outbox pending lookup with `FOR UPDATE SKIP LOCKED` and require the caller to keep the transaction open through publish/mark.
- [x] Add PostgreSQL Testcontainers tests for round trips, constraints, cascade deletion, payment/expiry locking, and concurrent Outbox claiming.
- [x] Keep domain free of persistence/framework imports.

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

## Verification Evidence

- `./mvnw.cmd -pl infrastructure -am -DskipTests compile` — pass.
- Targeted Phase 4 persistence tests — pass: 20 tests after the final transaction-lock contract adjustment.
- `./mvnw.cmd -pl infrastructure -am test` — pass: 227 domain + 75 application + 59 infrastructure tests.
- `./mvnw.cmd -pl bootstrap -am test '-Dsurefire.failIfNoSpecifiedTests=false'` — pass: 227 domain + 75 application + 59 infrastructure + 80 presentation + 9 bootstrap tests.
- `git diff --check` — pass; Git only reported the repository's existing LF/CRLF conversion warnings.

## Errors Encountered and Resolutions

1. PowerShell parsed the comma-separated `-Dtest` value as a command expression, so Maven never started. The fix was to quote the Maven system-property argument: `'-Dtest=...'`. The targeted suite then ran successfully.
2. Three repository tests initially compared nanosecond `Instant` values exactly, while PostgreSQL `timestamp(6)` stores microsecond precision. The fix was to assert timestamps within two microseconds; the final targeted and full suites passed.
3. The first timestamp assertion fix used AssertJ `Duration`, but the `Instant` assertion overload requires a `TemporalOffset`. The fix was `within(2, ChronoUnit.MICROS)`; compilation and all tests then passed.

Expected duplicate-key SQL warnings in constraint tests and Hibernate's first-run `constraint ... does not exist, skipping` messages are intentional schema/test setup output, not unresolved failures.

## Review

- `code-review` verdict: **APPROVED**.
- No actionable findings remain for Phase 4.
- Residual work is intentionally deferred to later phases: RabbitMQ relay/worker idempotency, expiry/payment business orchestration, payment callback, ticket issuance, and the final Docker-backed E2E flow.


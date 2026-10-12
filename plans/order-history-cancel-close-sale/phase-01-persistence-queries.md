# Phase 1: Persistence queries and error mapping

## Implementation Status

- [x] Domain port signatures added.
- [x] `InvalidOrderStateException.errorType()` → `CONFLICT`.
- [x] Infrastructure implementations.
- [x] Indexes.
- [x] Tests.

## Affected Areas

- `domain/src/main/java/com/tienphat/domain/repository/`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidOrderStateException.java`
- `infrastructure/src/main/java/com/tienphat/infrastructure/{order,payment,ticket,tickettype,event}/`
- matching `*RepositoryImplTest` and `DomainExceptionErrorTypeTest`

## Port Additions

```java
// OrderRepository
PageResult<Order> findAllByUserId(UUID userId, PageRequest pageRequest);   // createdAt desc, id desc, items attached
// PaymentRepository
List<Payment> findAllByOrderIds(Collection<UUID> orderIds);
// TicketRepository
PageResult<Ticket> findAllByOwnerUserId(UUID ownerUserId, PageRequest pageRequest); // issuedAt desc, id desc
// TicketTypeRepository / EventRepository
List<TicketType> findAllByIds(Collection<UUID> ids);
List<Event> findAllByIds(Collection<UUID> ids);
```

## Implementation Steps

1. [x] Add the port methods above.
2. [x] Override `InvalidOrderStateException.errorType()` to return `ErrorType.CONFLICT`.
3. [x] `OrderJpaRepository`:
   - `@Query(value = "select o.id from OrderJpaEntity o where o.userId = :userId", countQuery = "select count(o) from OrderJpaEntity o where o.userId = :userId") Page<UUID> findIdsByUserId(UUID, Pageable)`
   - `@EntityGraph(attributePaths = "items") List<OrderJpaEntity> findAllByIdIn(Collection<UUID>)`
   - `OrderRepositoryImpl.findAllByUserId`: page ids with `Sort createdAt desc, id desc`, load the
     graph, reorder by the id page, map. Empty id page → skip the second query.
4. [x] `PaymentJpaRepository.findAllByOrderIdIn(Collection<UUID>)`; empty input → `List.of()` without a query.
5. [x] `TicketJpaRepository.findAllByOwnerUserId(UUID, Pageable)` with `Sort issuedAt desc, id desc`.
6. [x] `TicketTypeRepositoryImpl` / `EventRepositoryImpl.findAllByIds` via `jpaRepository.findAllById`.
7. [x] Indexes (created by `ddl-auto: update`):
   - `orders`: `idx_orders_user_created (user_id, created_at)`
   - `tickets`: `idx_tickets_owner_issued (owner_user_id, issued_at)`, `idx_tickets_order_item (order_item_id)`

## Tests

- `OrderRepositoryImplTest`: newest-first order, `totalElements`, second page, other users excluded,
  items attached on every returned order, empty result.
- `PaymentRepositoryImplTest`: batch returns only requested orders; empty input.
- `TicketRepositoryImplTest`: owner paging + sort, other owners excluded.
- `TicketTypeRepositoryImplTest` / `EventRepositoryImplTest`: batch lookup skips unknown ids.
- `DomainExceptionErrorTypeTest`: `InvalidOrderStateException` → `CONFLICT`.

## Exit Criteria

`mvn -pl domain,infrastructure -am test` green.

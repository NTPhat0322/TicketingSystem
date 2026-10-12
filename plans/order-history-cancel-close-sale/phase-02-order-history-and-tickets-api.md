# Phase 2: Order history and my tickets API

## Affected Areas

- `application/src/main/java/com/tienphat/application/order/` (list orders)
- `application/src/main/java/com/tienphat/application/ticket/` (new package)
- `presentation/src/main/java/com/tienphat/presentation/order/`
- `presentation/src/main/java/com/tienphat/presentation/ticket/` (new package)
- `presentation/src/main/java/com/tienphat/presentation/config/UseCaseConfig.java`

## Application

| Use case | Command | Result | Rules |
|----------|---------|--------|-------|
| `ListMyOrdersUseCase` | `ListMyOrdersCommand(actor, pageRequest)` | `PageResult<OrderStatusResult>` | `requireAuthenticated`; `findAllByUserId(actor.userId())`; one `findAllByOrderIds`; map with existing `OrderStatusResult.fromOrder`. |
| `GetOrderTicketsUseCase` | `GetOrderTicketsCommand(orderId, actor)` | `List<TicketResult>` | Order absent in DB → `OrderNotFoundException` (404); `requireCanAccess(order.userId)`; tickets via `findAllByOrderItemId` per item, sorted `issuedAt, ticketCode`. Non-PAID order → empty list. |
| `ListMyTicketsUseCase` | `ListMyTicketsCommand(actor, pageRequest)` | `PageResult<TicketResult>` | `requireAuthenticated`; `findAllByOwnerUserId(actor.userId())`. |

All three are `@Transactional(readOnly = true)`.

```java
public record TicketResult(
        UUID ticketId, String ticketCode, TicketStatus status, Instant issuedAt, Instant checkedInAt,
        UUID orderItemId, UUID ticketTypeId, String ticketTypeName,
        UUID eventId, String eventName, String venueName, Instant eventStartTime, Instant eventEndTime) {}
```

`TicketResultAssembler` (package-private, built inside the two ticket use cases): one
`TicketTypeRepository.findAllByIds`, one `EventRepository.findAllByIds`; unknown type/event → `null` names.

## Presentation

| Endpoint | Response | Validation |
|----------|----------|------------|
| `GET /api/v1/orders?page=0&size=20` | `PageResponse<OrderResponse>` | `@Min(0) page`, `@Min(1) @Max(100) size`; add `@Validated` to `OrderController` |
| `GET /api/v1/orders/{orderId}/tickets` | `List<TicketResponse>` | UUID path |
| `GET /api/v1/tickets?page=0&size=20` | `PageResponse<TicketResponse>` | same paging bounds |

- New `TicketController` (`@RequestMapping("/api/v1/tickets")`, `@SecurityRequirement`).
  `SecurityConfig.anyRequest().authenticated()` already covers it.
- New `TicketDtoMapper` (MapStruct) with `toResponse`, `toResponses`, default `toPageResponse`.
- `OrderDtoMapper` gains a default `toPageResponse(PageResult<OrderStatusResult>)`.
- Wire the three use cases as `UseCase<…>` beans in `UseCaseConfig`.

## Tests

- Unit: `ListMyOrdersUseCaseTest` (payments joined by order id, order without payment, empty page
  skips payment query, unauthenticated → Forbidden), `GetOrderTicketsUseCaseTest` (owner, admin,
  other user → Forbidden, missing → NotFound), `ListMyTicketsUseCaseTest`, `TicketResultAssemblerTest`
  via the use cases (missing type/event → null names, single batch call each).
- WebMvc: `OrderControllerTest` (list 200 + paging params, size=101 → 400, 401 without JWT,
  tickets 200/403/404), new `TicketControllerTest`.

## Exit Criteria

`mvn -pl application,presentation -am test` green.

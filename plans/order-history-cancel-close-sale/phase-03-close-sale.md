# Phase 3: Manual close-sale endpoint

## Affected Areas

- `application/src/main/java/com/tienphat/application/event/`
- `presentation/src/main/java/com/tienphat/presentation/event/EventController.java`
- `presentation/src/main/java/com/tienphat/presentation/config/UseCaseConfig.java`

## Implementation Steps

1. [x] `CloseSaleCommand(UUID eventId, AuthorizationContext actor)`.
2. [x] `CloseSaleUseCase implements UseCase<CloseSaleCommand, EventResult>`, `@Transactional`,
   same shape as `PublishEventUseCase`: find → `EventNotFoundException`, `requireCanManage(organizerId)`,
   `event.close()`, save, `eventMapper.toResult`.
3. [x] `POST /api/v1/events/{id}/close-sale`, `@PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")`,
   `@SecurityRequirement`, `@Operation(summary = "Close ticket sales for an on-sale event")`.
4. [x] Bean `UseCase<CloseSaleCommand, EventResult> closeSaleUseCase` in `UseCaseConfig`.

## Behaviour

| Case | Result |
|------|--------|
| ON_SALE, owner ORGANIZER or ADMIN | 200, status `CLOSED` |
| DRAFT / PUBLISHED / CLOSED / CANCELLED | 400 (`InvalidEventStateException`, existing convention) |
| other organizer | 403 |
| CUSTOMER | 403 (`@PreAuthorize`) |
| unknown id | 404 |

Redis stock keys, live holds, and ticket types are left untouched (plan decision 4).

## Tests

- `CloseSaleUseCaseTest`: success saves CLOSED; wrong owner → Forbidden and no save;
  not ON_SALE → InvalidEventState and no save; missing → NotFound.
- `EventControllerTest` `@Nested CloseSale`: 200, 403 customer, 401 anonymous, 400/404 mapping.

## Exit Criteria

`mvn -pl application,presentation -am test` green.

# Phase 2: Event Use-Cases

## P1 Stories Satisfied
- "As an organizer, I want to create a new Event so that it exists in `DRAFT` status and I can start configuring ticket types. Accepted when: `CreateEventUseCase` persists a new `Event` via `EventRepository.save()` and returns its id."
- "As an organizer, I want to update an existing Event's details so that I can fix mistakes or adjust schedule before it goes on sale. Accepted when: `UpdateEventUseCase` loads the `Event` by id, applies changes, and saves — throwing `InvalidEventStateException` if `status != DRAFT`. No field is editable once the event has left `DRAFT`."
- "As an organizer, I want to view a single Event's details by id. Accepted when: `GetEventUseCase` returns the mapped `EventResult` or a not-found error when the id doesn't exist."
- "As any user, I want to list Events with pagination so that I can browse available events. Accepted when: `ListEventsUseCase` returns a paged result using a newly added `EventRepository.findAll(...)` port method."
- "As an organizer, I want to deactivate an Event so that it stops being sellable. Accepted when: `DeactivateEventUseCase` calls `Event.cancel()` and saves — surfacing `InvalidEventStateException` if the current status cannot transition to `CANCELLED`."

## Requirements
All 5 Event use-cases exist, compile against the Phase 1 foundation, and are independently unit-tested. `Event` gains the `updateDetails(...)` mutator this phase needs — a real gap found while grounding in the current `Event.java` source, which today has no way to change `name`/`description`/`venueName`/schedule after `create()`. `testing: --tdd` — every item below is written as a failing test before the production code that satisfies it.

### Tests to Write First
Write each of these (red) before the class it targets exists, then implement just enough to turn it green:
- `EventTest#updateDetails_*` (domain, added to the existing `EventTest.java`): happy path from `DRAFT`; `InvalidEventStateException` from every non-`DRAFT` status; `InvalidEventDataException` on blank/null fields; `InvalidEventScheduleException` on bad start/end or sale-window ordering; a rejected call leaves the entity unchanged. Drives the `Event.updateDetails(...)` mutator (Step 1).
- `CreateEventUseCaseTest`: happy path (saves, returns `EventResult` with `status = DRAFT`); `InvalidEventDataException` (blank/null required field); `InvalidEventScheduleException` (bad ordering). Drives `CreateEventUseCase`/`CreateEventCommand` (Step 3) — write after `EventMapper` exists (Step 2), since the use-case needs it to compile.
- `UpdateEventUseCaseTest`: happy path (status stays `DRAFT`, fields change); `EventNotFoundException` (id not found); `InvalidEventStateException` (status ≠ `DRAFT`); `InvalidEventDataException`/`InvalidEventScheduleException` (bad new values). Drives `UpdateEventUseCase`/`UpdateEventCommand` (Step 4).
- `GetEventUseCaseTest`: happy path; `EventNotFoundException`. Drives `GetEventUseCase` (Step 5).
- `ListEventsUseCaseTest`: happy path with a mocked `PageResult<Event>`, asserting `EventRepository.findAll(PageRequest)` is called exactly once and the result is mapped item-by-item. Drives `ListEventsUseCase` (Step 5).
- `DeactivateEventUseCaseTest`: happy path (`Event.cancel()` then save); `EventNotFoundException`; `InvalidEventStateException` (illegal transition, e.g. from `CLOSED`). Drives `DeactivateEventUseCase`/`DeactivateEventCommand` (Step 6).

## Files to Create/Modify
- MODIFY `domain/src/main/java/com/tienphat/domain/model/Event.java` — add `updateDetails(String name, String description, String venueName, Instant startTime, Instant endTime, Instant saleStartTime, Instant saleEndTime)`: throws `InvalidEventStateException` if `status != DRAFT`; otherwise reuses the same `requireNotBlank`/`requireNotNull` field checks and the same `startTime < endTime` / `saleStartTime < saleEndTime` ordering checks (`InvalidEventScheduleException`) that `create()` already runs; bumps `updatedAt`.
- MODIFY `domain/src/test/java/com/tienphat/domain/model/EventTest.java` — add coverage for `updateDetails` (happy path from `DRAFT`, `InvalidEventStateException` from every other status, `InvalidEventDataException` on blank/null fields, `InvalidEventScheduleException` on bad ordering, and that a rejected call leaves the entity untouched — matching the existing `updateProfile`-style convention noted in `plans/domain-layer/plan.md`).
- CREATE `application/src/main/java/com/tienphat/application/event/EventResult.java` — flat record mirroring `Event`'s fields (`id`, `organizerId`, `name`, `description`, `venueName`, `startTime`, `endTime`, `saleStartTime`, `saleEndTime`, `status`, `createdAt`, `updatedAt`).
- CREATE `application/src/main/java/com/tienphat/application/event/EventMapper.java` — `@Mapper(componentModel = "spring")`, one method `EventResult toResult(Event event)`.
- CREATE `application/src/main/java/com/tienphat/application/event/CreateEventCommand.java` — record with every `Event.create` field except `id` (generated by the use-case).
- CREATE `application/src/main/java/com/tienphat/application/event/CreateEventUseCase.java` — `UseCase<CreateEventCommand, EventResult>`, `@Transactional`; generates `id`, calls `Event.create(...)`, saves via `EventRepository`, maps with `EventMapper`.
- CREATE `application/src/main/java/com/tienphat/application/event/UpdateEventCommand.java` — record with `id` plus every `updateDetails` field.
- CREATE `application/src/main/java/com/tienphat/application/event/UpdateEventUseCase.java` — `UseCase<UpdateEventCommand, EventResult>`, `@Transactional`; `EventRepository.findById(id).orElseThrow(() -> new EventNotFoundException("Event " + id + " not found"))`, calls `updateDetails(...)`, saves, maps. (`EventNotFoundException` has only a `(String message)` constructor, matching every other `DomainException` subclass — `EventNotFoundException::new` is not a valid `Supplier<EventNotFoundException>` and would not compile; use a lambda everywhere this exception is thrown.)
- CREATE `application/src/main/java/com/tienphat/application/event/GetEventUseCase.java` — `UseCase<UUID, EventResult>` (bare id, no wrapper query type), read-only, throws `EventNotFoundException` on empty `findById`.
- CREATE `application/src/main/java/com/tienphat/application/event/ListEventsUseCase.java` — `UseCase<PageRequest, PageResult<EventResult>>` (domain's `PageRequest`/`PageResult` used directly, no bespoke wrapper), read-only, delegates the actual slicing to `EventRepository.findAll(...)`.
- CREATE `application/src/main/java/com/tienphat/application/event/DeactivateEventCommand.java` — record wrapping `id`.
- CREATE `application/src/main/java/com/tienphat/application/event/DeactivateEventUseCase.java` — `UseCase<DeactivateEventCommand, EventResult>`, `@Transactional`; loads by id (`EventNotFoundException`), calls `Event.cancel()`, saves, maps.
- CREATE `application/src/test/java/com/tienphat/application/event/CreateEventUseCaseTest.java`
- CREATE `application/src/test/java/com/tienphat/application/event/UpdateEventUseCaseTest.java`
- CREATE `application/src/test/java/com/tienphat/application/event/GetEventUseCaseTest.java`
- CREATE `application/src/test/java/com/tienphat/application/event/ListEventsUseCaseTest.java`
- CREATE `application/src/test/java/com/tienphat/application/event/DeactivateEventUseCaseTest.java`

## Steps
1. Write `EventTest#updateDetails_*` (red), then add the `Event.updateDetails(...)` mutator to make it pass (green) — this unblocks `UpdateEventUseCase` and must land before it.
2. Create `EventResult` and the MapStruct `EventMapper` (`Event → EventResult` only; `Command → Event` construction stays a direct factory/mutator call in each use-case, since MapStruct cannot express `Event`'s validated-construction order). No test precedes this — it's a mechanical mapping interface exercised indirectly by every use-case test below.
3. Write `CreateEventUseCaseTest` (red), then implement `CreateEventUseCase` + `CreateEventCommand` (green).
4. Write `UpdateEventUseCaseTest` (red), then implement `UpdateEventUseCase` + `UpdateEventCommand` (green). Every `orElseThrow` call site uses a lambda (`() -> new EventNotFoundException(...)`), never a bare `::new` method reference — `EventNotFoundException` has no no-arg constructor.
5. Write `GetEventUseCaseTest` and `ListEventsUseCaseTest` (red), then implement `GetEventUseCase` and `ListEventsUseCase` (green) — both read-only, no `@Transactional`.
6. Write `DeactivateEventUseCaseTest` (red), then implement `DeactivateEventUseCase` + `DeactivateEventCommand` (green).
7. Run the full `application` + `domain` test suite and confirm every write use-case's `@Transactional` boundary wraps exactly one `EventRepository.save()` call.

## Success Criteria
- All 5 Event use-case classes compile and implement `UseCase<I, O>`; `CreateEventUseCase`, `UpdateEventUseCase`, `DeactivateEventUseCase` are `@Transactional`, `GetEventUseCase`/`ListEventsUseCase` are not.
- Test coverage per use-case:
  - `CreateEventUseCaseTest`: happy path (saves, returns `EventResult` with `status = DRAFT`); `InvalidEventDataException` (blank/null required field); `InvalidEventScheduleException` (bad start/end or sale-window ordering).
  - `UpdateEventUseCaseTest`: happy path (status stays `DRAFT`, fields change); `EventNotFoundException` (id not found); `InvalidEventStateException` (status ≠ `DRAFT`); `InvalidEventDataException`/`InvalidEventScheduleException` (bad new values).
  - `GetEventUseCaseTest`: happy path; `EventNotFoundException`.
  - `ListEventsUseCaseTest`: happy path with a mocked `PageResult<Event>`, asserting `EventRepository.findAll(PageRequest)` is called exactly once and the result is mapped item-by-item (no in-memory pagination).
  - `DeactivateEventUseCaseTest`: happy path (`Event.cancel()` then save); `EventNotFoundException`; `InvalidEventStateException` (illegal transition, e.g. from `CLOSED`).
- `mvnw -pl application,domain test` passes.

## Risks
- Adding `updateDetails` reopens `Event`, a module previously signed off as complete: mitigated by reusing `create()`'s exact validation helpers/exception types (no new invariant surface) and pinning the addition with its own `EventTest` cases in the same commit.
- `UpdateEventCommand` accepting every field means a caller that omits one silently nulls it out (full-overwrite semantics): out of scope for this phase per FR-08 (presentation validates required fields before calling the use-case) — flagged here so the presentation-layer request DTO design doesn't miss it.

# Plan: Domain Module (Ticketing / Flash-Sale System)
Status: ✅ Complete
Date: 2026-09-15
Mode: Hard

## Overview
Build the `domain` Maven module (pure Java 21, no Spring/JPA) for the ticketing/flash-sale system: 8 aggregates/entities, their state-machine enums, repository ports, one tech-agnostic stock-cache port, and a shared `DomainException` hierarchy — mirroring the settled design in `docs/design/ticketing-domain-design.md` §3–4 and following the Clean Architecture convention in `D:\FPT\Java-SpringBoot\build-architecture-spring_p2.md` (a personal reference doc kept outside this repo, one directory above the repo root — not checked into git, cross-checked by hand during planning since it isn't grep/glob-able from inside the repo). Application-layer use cases, JPA persistence entities, controllers, and Redis/RabbitMQ adapters are explicitly out of scope for this plan.

**Design change taken mid-planning (2026-09-15):** `events.flash_sale_mode` and the dual inventory strategy it selected were removed from the design doc. Every event now reserves stock through Redis+Lua; there is no Postgres optimistic-lock reserve path. This simplifies Phases 3 and 4 (no flag field on `Event`, no `reserve()`/`release()` pair on `TicketType`) and retires the plan's only HIGH risk. `docs/design/ticketing-domain-design.md` §2.4 carries the reasoning and the accepted costs.

## Scope
In scope: `domain/src/main/java/com/tienphat/domain/**` (entities, enums, value objects, exceptions, repository ports, `StockCachePort`) + matching JUnit5 unit tests under `domain/src/test/java/...` + the `domain/pom.xml` test-dependency addition needed to run them.

Out of scope: application use cases, JPA `@Entity` classes, MapStruct mappers, REST controllers, Redis/RabbitMQ adapter implementations, Flyway/Hibernate schema. `OrderEventPublisherPort` is deliberately **not** created — the transactional outbox pattern (design doc §2.5) means "publish intent" is just persisting an `OutboxEvent` row via `OutboxEventRepository` in the same transaction as the triggering write; actual dispatch is an infrastructure-side poller with no domain-facing port.

## Aggregate List
| Aggregate root | Child entities | Own repository? |
|---|---|---|
| `User` | — | yes — `UserRepository` |
| `Event` | — | yes — `EventRepository` |
| `TicketType` | — | yes — `TicketTypeRepository` (own aggregate root, **not** a child of `Event` — one Redis stock key maps to one `TicketType`, and loading an `Event` should not drag every sibling ticket type along on the payment-confirmation write path) |
| `Order` | `OrderItem` | yes — `OrderRepository` (no separate `OrderItemRepository`; `OrderItem` always loaded/saved through `Order`, but keeps its own UUID since `tickets.order_item_id` references it) |
| `Ticket` | — | yes — `TicketRepository` |
| `Payment` | — | yes — `PaymentRepository` |
| `OutboxEvent` | — | yes — `OutboxEventRepository` |

## Package Tree
```
domain/src/main/java/com/tienphat/domain/
  model/        -- entities + their status enums + small supporting enums, colocated
    User.java, UserRole.java
    Event.java, EventStatus.java
    TicketType.java, TicketTypeStatus.java
    Order.java, OrderStatus.java, OrderItem.java
    Ticket.java, TicketStatus.java
    Payment.java, PaymentStatus.java, PaymentProvider.java
    OutboxEvent.java, OutboxEventStatus.java, OutboxEventType.java, AggregateType.java
    UuidV7Generator.java   -- package-private helper, used only by Order.create()
  vo/
    Money.java
  exception/
    DomainException.java  -- abstract base
    InvalidMoneyException.java
    InvalidUserDataException.java
    InvalidEventStateException.java, InvalidEventScheduleException.java
    InvalidTicketTypeDataException.java, InsufficientStockException.java,
    TicketTypeNotAvailableException.java, InvalidReservationQuantityException.java
    InvalidOrderStateException.java, InvalidOrderItemException.java, DuplicatePaymentException.java
    InvalidTicketDataException.java, TicketAlreadyCheckedInException.java, InvalidTicketStateException.java
    InvalidPaymentDataException.java, InvalidPaymentStateException.java
    InvalidOutboxEventDataException.java, InvalidOutboxEventStateException.java
  repository/
    UserRepository.java, EventRepository.java, TicketTypeRepository.java,
    OrderRepository.java, TicketRepository.java, PaymentRepository.java, OutboxEventRepository.java
  port/
    StockCachePort.java       -- tech-agnostic Redis abstraction, no Redis/Jedis/Lettuce types
    ReservationResult.java    -- SUCCESS / OUT_OF_STOCK / USER_LIMIT_EXCEEDED, returned by tryReserve

domain/src/test/java/com/tienphat/domain/
  vo/MoneyTest.java
  model/UserTest.java
  model/EventTest.java, EventStatusTest.java
  model/TicketTypeTest.java, TicketTypeStatusTest.java
  model/OrderTest.java, OrderStatusTest.java, UuidV7GeneratorTest.java
  model/TicketTest.java, TicketStatusTest.java
  model/PaymentTest.java, PaymentStatusTest.java
  model/OutboxEventTest.java, OutboxEventStatusTest.java
```

## Cross-Cutting Conventions
| Concern | Decision |
|---|---|
| Construction | `@Builder(access = AccessLevel.PRIVATE)` on every entity + `@Getter`; only public **named static factory methods** (`register()`, `create()`, `issueFor()`, `initiate()`, `record()`) call the private builder, after running invariant checks. Nobody outside the class can bypass validation via a bare builder. |
| Reconstitution | Every entity also exposes `public static reconstitute(...)` taking **all** persisted fields, including the ones the creation factory forces (`soldQuantity`, `version`, `status`, `createdAt`/`updatedAt`). **Null checks only — no business validation**: the row was valid when written, so re-validating means a later rule change locks the system out of its own history. For persistence mappers only, stated in each method's javadoc; application code always goes through the creation factory. Added 2026-09-16 after Phase 4 surfaced that `findById` was otherwise unimplementable — the creation factory ignores stored state, so a tier with 500 sold would load as 0. |
| Mutation | No `@Setter`, no `@Data`. State changes only via guarded instance methods that check the transition table / invariant and throw a `DomainException` subclass on violation. Fields are non-`final` (except `id`) to allow internal reassignment. |
| Identity | `@EqualsAndHashCode(of = "id")` per entity — never Lombok's default field-based equals (breaks on mutable state / null pre-save id). No shared `AggregateRoot<ID>` base class (see Risks/decisions below). |
| Value objects | `Money` uses Lombok `@Value` (fully immutable, full-field equality) — a deliberately different Lombok pattern from entities, since VOs and entities have different equality semantics. |
| Collections | `Order.getItems()` (and any future multi-valued getter) returns `Collections.unmodifiableList(new ArrayList<>(items))` — never the live backing list. |
| IDs | Raw `java.util.UUID` everywhere. No `OrderId`/`UserId` wrapper types (matches doc #2's convention and the codebase's minimalism). |
| State machines | Plain enum + `private static final EnumMap<Status, Set<Status>> TRANSITIONS` + `boolean canTransitionTo(Status target)` instance/static helper. No State/Strategy class-per-status (over-engineering for ≤5 statuses), no ad-hoc `if` duplication across methods (duplicated legality checks is how double-processing bugs enter — relevant given payment webhook replay risk). |
| Exceptions | `DomainException` abstract base (message-only constructor, matches doc #2 exactly) + one concrete, **non-abstract** subclass per invariant category, all in `exception/` shared across aggregates (e.g. `DuplicatePaymentException` is defined once in Phase 5, reused unmodified by Phase 7). |
| Timestamps | `java.time.Instant` for every `*_at` field. Entities with an `updated_at` column (`User`, `Event`, `TicketType`, `Order`) bump it in every guarded mutation method. |
| Money | `vo.Money` wraps `BigDecimal`, scale 2, `RoundingMode.HALF_UP`, non-negative, no currency field (single-currency VND system). |
| Id generation | Two patterns, both intentional: `User`/`Event`/`TicketType` factories take `id` as a caller-supplied `UUID` parameter (id generation left to the future application layer); `Ticket`/`Payment`/`OutboxEvent` factories self-generate `id = UUID.randomUUID()` internally (no upstream reason to pre-generate); `Order` is a third, explicitly-justified pattern (internal `UuidV7Generator`, design doc §2.1). All three guarantee `id` exists before the object is constructable — the split is about *who* generates it, not *whether* it's pre-set. Added per plan-review finding (2026-09-15, NOTED). |

### Decision: no `AggregateRoot<ID>` base class
Considered a tiny `AggregateRoot<ID>` base carrying `id` + `equals`/`hashCode`. Rejected: only 7 aggregates, the savings are ~5 lines/class; a base class forces either inheritance-unfriendly `@Builder` (needs `@SuperBuilder`, which changes the builder API surface and adds a Lombok interaction the rest of the codebase doesn't use elsewhere) or duplicated field declarations anyway. `@EqualsAndHashCode(of = "id")` applied per-class is simpler, explicit, and has no inheritance side effects. Documented here so it isn't re-litigated per phase.

## Phases
- [x] Phase 1: Foundation — `DomainException`, `Money`, package skeleton, test dependencies, shared conventions
- [x] Phase 2: User aggregate — establishes the entity pattern (entity + enum + repository + exceptions + tests)
- [x] Phase 3: Event aggregate — `EventStatus` state machine, schedule validation
- [x] Phase 4: TicketType aggregate — `confirmSale()` as the sole `soldQuantity` writer, `releaseSale()`/`close()`, `StockCachePort` + `ReservationResult`
- [x] Phase 5: Order + OrderItem aggregate — UUIDv7 id, guarded `pay()`/`expire()`/`cancel()`/`fail()`
- [x] Phase 6: Ticket aggregate — `issueFor()`/`checkIn()`
- [x] Phase 7: Payment aggregate — `markSuccess()`/`markFailed()`, reuses `DuplicatePaymentException`
- [x] Phase 8: OutboxEvent — transactional outbox row model

## Research Summary
Two independent researcher passes converged on: (1) the package tree above, extending doc #2's `domain.entity/exception/repository` convention with `model/vo/port` naming and colocated status enums; (2) `TicketType` as its own aggregate root (not nested under `Event`) to avoid N+1/lock contention; (3) the no-`@Setter`/private-builder/guarded-method mutation pattern as the single biggest landmine to avoid, since doc #2's own JPA example (`UserJpaEntity` with `@Setter`) is persistence code, not a domain-entity pattern, and would leak mutable state if copied verbatim; (4) enum + transition-table state machines over class-per-state; (5) `Money` as the only value object, no ID wrapper types; (6) `StockCachePort` as the sole domain-facing port, with no `OrderEventPublisherPort` because the outbox pattern replaces it. All decisions above are treated as settled inputs for this plan, not re-opened.

## Dependencies
None external. `domain` currently depends only on Lombok (`optional`). Phase 1 adds `junit-jupiter` and `assertj-core` as `test`-scope dependencies (versions managed transitively via the `spring-boot-starter-parent` BOM already on the root `pom.xml`, so no explicit `<version>` needed). No new **runtime** dependency is introduced anywhere in this plan — UUIDv7 generation (Phase 5) is hand-rolled rather than pulling in a library, see Risks. Future `application`/`infrastructure` modules will depend on `domain`, never the reverse.

## Risks
- ~~HIGH: `TicketType.reserve()` vs `confirmSale()` double-counting ambiguity.~~ **Resolved 2026-09-15** by a design decision taken mid-planning: `events.flash_sale_mode` was removed and every event now reserves through Redis+Lua (design doc §2.4, rewritten). `soldQuantity` consequently has exactly one writer — `confirmSale()`, called only by `ConfirmPaymentUseCase` on payment SUCCESS — so the double-counting call pattern the risk described is no longer expressible. `TicketType` has no `reserve()` method at all; reserve/release live behind `StockCachePort`. Kept here rather than deleted because it records *why* the entity has an asymmetric API (a Postgres-side `confirmSale`/`releaseSale` pair but no Postgres-side reserve).
- MEDIUM: Redis is now a deliberate single point of failure for all ticket sales (design doc §2.4) — there is no Postgres fallback path, because a fallback is the second code path reintroduced, triggered by an outage instead of a flag. Domain-layer consequence only: `StockCachePort` has no "degraded mode" method and callers must fail closed. Operational mitigation (Redis HA, AOF) is out of scope for this plan and tracked in design doc §6.
- MEDIUM: UUIDv7 is hand-rolled (Phase 5) rather than adding a library dependency, since `domain`'s pom currently has zero non-Lombok dependencies and the plan avoids introducing one for a single generator method. Mitigation: minimal RFC 9562 §5.7-shaped implementation (48-bit unix ms timestamp + version/variant nibbles + random tail), unit-tested for correct version/variant bits and non-decreasing timestamp ordering across successive calls. Revisit if a vetted library (e.g. `com.github.f4b6a3:uuid-creator`) is later approved — that's a build-file decision for whoever picks up Phase 5, not pre-decided here.
- MEDIUM: `Order.expire()` vs `Payment.markSuccess()` race (webhook succeeds just as the expire-delay worker fires). Domain-level mitigation: `expire()` is strictly guarded (`PENDING_PAYMENT → EXPIRED` only) and throws `InvalidOrderStateException` if the order already flipped to `PAID`. The future `ExpireOrderUseCase` (out of scope) must catch/ignore that specific exception rather than propagate it — noted here so it isn't missed later.
- LOW: Enum transition tables can silently drift from the DB `status varchar` values if either side is edited independently without the other. Mitigation: each phase's `*StatusTest` exhaustively asserts the full transition table (every valid pair returns `true`, every other pair returns `false`), so an accidental omission fails a test immediately rather than surfacing as a runtime bug.
- LOW: `Payment.provider` and `OutboxEvent.eventType`/`aggregateType` are modeled as enums (`PaymentProvider{VNPAY,MOMO,STRIPE}`, `OutboxEventType{ORDER_CREATED,ORDER_EXPIRED,PAYMENT_SUCCESS}`, `AggregateType{ORDER,PAYMENT}`) rather than plain `String`, matching the exact values design doc §4 lists today. Trade-off: adding a new provider or outbox event type later requires an enum edit + recompile across dependents. Acceptable for now; flagged as revisitable if the list grows frequently.
- LOW: No automatic `updatedAt` bump mechanism (no AOP/interceptor at the domain layer, by design — domain is pure Java). Each guarded mutation method explicitly sets `updatedAt = Instant.now()`; tests assert non-decreasing timestamps only (loose bound, since `Instant.now()` resolution can tie within the same test).

## Session Notes
<!-- Updated by cook automatically — do not edit manually -->

**Last active:** 2026-09-16
**Phase in progress:** all phases complete — `--hard` review gate approved 2026-09-16
**Status:** Phases 1–8 done — `mvnw test -pl domain` green, **189/189 pass** (14 `MoneyTest`, 17 `UserTest`, 19 `EventTest`, 5 `EventStatusTest`, 29 `TicketTypeTest`, 3 `TicketTypeStatusTest`, 23 `OrderTest`, 5 `OrderStatusTest`, 5 `UuidV7GeneratorTest`, 16 `TicketTest`, 4 `TicketStatusTest`, 18 `PaymentTest`, 5 `PaymentStatusTest`, 20 `OutboxEventTest`, 6 `OutboxEventStatusTest`). This run is the plan's overall done criterion (phase-08 Success Criteria).

### Decisions made this session
- Used `junit-jupiter` + `assertj-core` directly rather than `spring-boot-starter-test`, keeping Spring off the `domain` test classpath entirely. Verified with `mvnw dependency:tree -pl domain`: compile scope is Lombok (optional) only; everything else is `test` scope and non-transitive, so `application`/`infrastructure` will never see it.
- Empty packages (`model/`, `repository/`, `port/`) were **not** pre-created with placeholder files — Java has no notion of an empty package and git does not track empty directories. Each appears when its first class lands in Phases 2–8.
- `Money.normalise()` applies scale 2 / `HALF_UP` on **every** construction and every arithmetic result, not just at `of()`. Reason: `@Value`'s generated `equals` delegates to `BigDecimal.equals`, which is scale-sensitive (`10.0` != `10.00`), so without normalising everywhere, value equality would be surprising. Pinned by the `equality_isByValueNotIdentity` test.
- `subtract()` down to exactly zero is allowed (only a strictly negative result throws) — added as its own test since the boundary was not spelled out in the phase file.
- **Phase 2:** an explicit private all-args constructor is written by hand next to `@Builder(access = PRIVATE)`, even though it looks redundant. Verified with a throwaway probe class + `javap`: `@Builder` alone generates a **package-private** all-args constructor, so any sibling class in `model/` could construct an entity that skipped factory validation. The private constructor closes that hole; `@Builder` then feeds through it. This is now part of the entity pattern for Phases 3–8.
- `User.changeRole()` to the role the user already holds is a permitted no-op (still bumps `updatedAt`) rather than an error — callers cannot distinguish a redundant write from a real one, and rejecting it would push that check up into the application layer for no benefit.
- `phone` is nullable in both `register()` and `updateProfile()` (matches design doc §4); `fullName` is not. Pinned by `register_acceptsNullPhone` and `updateProfile_phoneOptionalFullNameRequired`.
- `updateProfile()` validates before assigning anything, so a rejected call leaves the entity untouched — pinned by `updateProfile_doesNotMutateOnValidationFailure`, since partial mutation on failure is the classic bug in this shape of method.

- **Phase 3 (deviation from the phase file):** added `InvalidEventDataException`, which phase-03 did not list. The phase file specified only schedule and state-transition exceptions, which would have left `Event.create()` accepting a null `id` or blank `name` — inconsistent with `User.register()` and a real hole in an aggregate root. Schedule violations still throw `InvalidEventScheduleException`; the new type covers missing/blank required fields only.
- **Phase 3:** all four guarded methods route through one private `transitionTo(EventStatus)` that consults `EventStatus.canTransitionTo`. The transition rule is stated once (in the enum's table) instead of four times, so `cancel()`'s "any non-terminal status" needs no special case.
- **Phase 3:** `EventStatusTest` restates the transition table independently and sweeps all 25 `(from, to)` pairs. Restating it is deliberate — a single-source assertion would pass no matter what the enum said.
- **Phase 3:** kept the reachability sweep introduced for phase-04 (`everyStatus_isReachable`). Dead enum constants have now been caught twice in review (`Order.FAILED`, `TicketTypeStatus.CLOSED`), so every status-bearing aggregate gets this test.
- **Phase 3:** `Event.create()` permits `saleEndTime` past `startTime` (at-the-door sales). Pinned by `create_allowsSaleOverlappingEventStart` so the permission is a decision on record, not an oversight.

- **Phase 4:** mutation-tested the guard-ordering claim instead of trusting it. Temporarily changed `confirmSale()`'s guard from `status == CLOSED` to `status != ACTIVE`: exactly 1 of 22 `TicketTypeTest` cases failed — `confirmSale_onSoldOutSurfacesDivergenceSignal` — confirming it is the sole test pinning that edit. Reverted; suite green again.
- **Phase 4:** `releaseSale()` is permitted on a `CLOSED` type (counter decrements, status stays `CLOSED`). The phase file did not guard it and did not say either way; refunds routinely arrive after an organizer stops sales, and a cancelled event is nothing *but* refunds. Pinned by `releaseSale_onClosedStaysClosed`.
- **Phase 4:** phase-04 states the invariant as `SOLD_OUT ⟺ soldQuantity == totalQuantity`. Strictly, the ⟸ direction fails for a type closed at capacity (`CLOSED` with `soldQuantity == totalQuantity`). The guard reasoning only needs ⟹, so nothing changes; the `TicketType` javadoc scopes the invariant to non-`CLOSED` statuses.
- **Phase 4:** "no `reserve` method" is enforced by a test (`noReserveMethodOnEntity`, reads declared method names via reflection) in addition to the one-time Success Criteria grep, so the constraint survives past this session. Reflection here only inspects names — it does not construct state, so plan.md's no-reflection rule is intact.
- **Phase 4:** `StockCachePort` javadoc records fail-closed behaviour and the warm-only-before-holds rule directly on the port, since the infrastructure adapter author will read the interface, not the design doc.
- **Reconstitution gap — found during Phase 4, resolved 2026-09-16 (user approved).** No phase defined how `infrastructure` rebuilds an entity from a DB row: every creation factory forces fresh state (`Instant.now()`, `soldQuantity = 0`, initial status) and both constructor and builder are private, so `findById` was unimplementable — a tier with 500 sold would have loaded as 0 and the next `confirmSale` would have written that back. Fixed by adding `reconstitute(...)` to `User`, `Event` and `TicketType` (see the Reconstitution row in Cross-Cutting Conventions). Null checks only; no business re-validation, so a future rule change cannot make old rows unreadable. Additive — the 76 existing tests were untouched, 7 new ones added.
  - Rejected alternatives: public `builder()` (reopens exactly the hole the private constructor closes), package-private builder with mappers in the same package (impossible across Maven modules), reflection in the mapper (breaks silently on field rename), setters (destroys the Phase 2 entity pattern).
  - Accepted cost: `reconstitute()` is a wider door than `create()` and could be misused to build an invalid entity. Mitigated by null-checks-only, "persistence mappers only" in every javadoc, and a name nobody reaches for by accident. Java offers nothing narrower across a module boundary.

- **Phase 5 (deviation from the phase file):** added `InvalidOrderDataException`, which phase-05 did not list. Its three exceptions cover state transitions, line items and duplicate payment — none fits a null `userId` in `create()` or a null column in `reconstitute()`. Same reasoning as the Phase 3 `InvalidEventDataException` deviation, and it keeps `Order` consistent with `User`/`Event`/`TicketType`.
- **Phase 5 (extension):** `UuidV7Generator` is also used for `OrderItem` ids, not only `Order.id` as phase-05 stated. The `order_items` row needs a primary key and minting it in `OrderItem.of()` keeps the line complete before the order is saved; the generator stays package-private, so this is not a wider API.
- **Phase 5:** `OrderItem.of()` is package-private (only `Order.addItem` may create a line) but `OrderItem.reconstitute()` is **public** — the persistence mapper lives in `infrastructure`, outside this package, and has to rebuild stored lines. Not a hole in the aggregate boundary: `of()` mints an id and derives `subtotal`, so it cannot reproduce a row, while `reconstitute()` takes both as stored and creates nothing.
- **Phase 5:** `Order.reconstitute()` takes `totalAmount` as stored rather than recomputing it from `items`. The row records what the buyer was actually charged; recomputing would silently paper over a persistence bug instead of surfacing it.
- **Phase 5:** mutation-tested the two claims that carry real weight, same method as Phase 4.
  - Replaced `DuplicatePaymentException` with `InvalidOrderStateException` in `pay()`: exactly 1 of 116 tests failed (`pay_twiceThrowsDuplicatePayment`), confirming that test is what pins the distinction a webhook retry depends on.
  - Replaced `getItems()`'s defensive copy with `return items`: exactly 1 of 116 failed (`getItems_isUnmodifiable`). Both reverted; suite green.
- **Phase 5:** `expire()` stays strict and throws from `PAID` rather than no-op'ing (phase-05 Risk, carried). Written into the method javadoc so the future `ExpireOrderUseCase` implementer sees it at the call site: a silent no-op would buy that one worker convenience at the cost of hiding illegal expiry attempts everywhere else.
- **Phase 5:** `OrderStatusTest` adds `everySettledStatus_isTerminal` — it asserts `PENDING_PAYMENT` is the only status with outgoing transitions, so a later "reopen a cancelled order" edit has to change a test that states the rule in words, not just a table entry.

- **Phase 6:** `Ticket` is the first aggregate with **no `updatedAt`** and therefore no `touch()`. The `tickets` table (design doc §4) has only `issued_at` and `checked_in_at`, which are events rather than bookkeeping — each written once and never moved. Deviating from the other entities here is the schema's call, not an oversight.
- **Phase 6:** `Ticket.id` stays a plain UUIDv4 rather than reusing `UuidV7Generator`. Orders are inserted in a burst during a flash sale, where primary-key insert locality matters; tickets are written once per sale and read by `ticket_code`. Recorded in the `issueFor()` javadoc so the asymmetry with `Order.id` reads as a decision, not an inconsistency.
- **Phase 6:** `ticketCode` is supplied by the caller, not generated in the entity. Its format (length, alphabet, checksum) is a customer-facing product decision; the entity only guards the lifecycle. `issueFor()` rejects blank/null.
- **Phase 6:** mutation-tested the `TicketAlreadyCheckedInException` vs `InvalidTicketStateException` split — the Success Criteria require them to be distinct classes, which is easy to assert and easy to have untested. Replacing the former with the latter in `checkIn()` failed exactly 2 of 136 tests (`checkIn_twiceThrowsAlreadyCheckedIn`, `reconstitute_preservesStoredState`). Reverted; suite green. Same two-layer shape as `DuplicatePaymentException` in Phase 5: a repeat of a legitimate action gets its own type so the caller never inspects `status`.
- **Phase 6:** `TicketStatus` has no `CHECKED_IN → ISSUED` edge, pinned by `checkedIn_isIrreversible`. Un-admitting a ticket would let the same QR pass the gate twice; a mis-scan is resolved by staff, not by the domain rewinding its own record.
- **Phase 6:** `checkIn()` on a `CANCELLED` ticket leaves `checkedInAt` null — asserted explicitly, since stamping before the guard is the classic version of this bug.
- **Phase 7:** `DuplicatePaymentException` is reused exactly as Phase 5 wrote it — verified: one `class DuplicatePaymentException` declaration in the tree, imported by both `Order` and `Payment`. The Success Criteria call for reuse, and a second copy under a Payment-flavoured name would have split one concept across two catch blocks.
- **Phase 7:** `Payment` carries `createdAt` per the phase file although design doc §4's `payments` block does not list a `created_at` column. Keeping it means the schema gains one column rather than the domain losing the only timestamp that says when the attempt was opened — `paidAt` is null until settlement, so without it a PENDING row has no age and no way to be swept. Flagged for the infrastructure phase.
- **Phase 7:** `initiate()` rejects a zero amount. `TicketType` permits a zero price, so a free tier is reachable — the consequence is that the future `CheckoutUseCase` must confirm a zero-total order **directly** and never open a payment for it, because no gateway can settle one. Recorded in the class javadoc so the constraint travels with the code.
- **Phase 7:** `PaymentStatus.FAILED` is terminal — no `FAILED → PENDING` retry edge, pinned by `failed_hasNoRetryEdge`. `payments.transaction_ref` is unique, so a genuine retry is a new attempt with a new reference; letting the old row reopen would make one reference describe two charges. This matches the phase-07 Risks entry and is a domain change if ever revisited, not a call-site workaround.
- **Phase 7:** `PaymentRepository.findByOrderId` returns `Optional<Payment>`, not `List` — `payments.order_id` is unique in design doc §4. Returning a list would invite call sites to handle a second payment that the schema forbids.
- **Phase 7:** mutation-tested the reconstitution convention itself rather than the duplicate-guard shape a third time. Adding the non-zero rule into `reconstitute()` failed exactly 1 of 159 tests (`reconstitute_doesNotRevalidateBusinessRules:241`). Reverted; suite green. That test is the only thing standing between a rules change and an unreadable history, so it needed to be shown load-bearing, not assumed.
- **Phase 7:** `javap -p` on `Payment` confirms zero setters, a private constructor, a private `builder()`, and exactly two public mutators (`markSuccess`, `markFailed`).
- **Phase 8 (deviation):** `OutboxEventType` carries the `AggregateType` it belongs to, and `record()` rejects a mismatched pair (`PAYMENT_SUCCESS` filed under `ORDER`). Not in the phase file. The two columns are independent in the schema, so a mismatch would pass every downstream check while the publisher routes by one field and the consumer looks up the wrong aggregate by the other. Cost is one comparison at write time. Mutation-tested: deleting the check failed exactly 1 of 185 tests (`record_rejectsMismatchedAggregateType:83`). Reverted; suite green.
- **Phase 8:** `OutboxEventStatus` is the first non-monotonic status machine in this domain — `FAILED → PENDING` is a real backward edge, because a publish failure is usually a briefly-unavailable broker rather than a wrong event. `PUBLISHED` stays terminal: re-publishing a delivered row manufactures exactly the duplicate delivery the outbox pattern is meant to bound.
- **Phase 8 (open risk, not fixable in domain):** there is no attempt counter. `outbox_events` (design doc §4) has no `retry_count` column, so a permanently-failing event can cycle `FAILED → PENDING → FAILED` forever. Bounding it needs a schema column plus a poller-side cap or dead-letter table. Documented in the `OutboxEvent` class javadoc; **carry into the infrastructure phase.**
- **Phase 8:** `OutboxEvent` has no `updatedAt`/`touch()`, same as `Ticket` — `outbox_events` has only `created_at` and `published_at`.
- **Phase 8:** `OutboxEventRepository.findAllPending()` javadoc carries two implementation constraints the domain cannot enforce: the recording transaction must commit before rows are visible, and multiple publisher instances need `FOR UPDATE SKIP LOCKED` or two pollers publish the same row.
- **Phase 8:** final Success Criteria verified by tooling, not assertion — `grep -rn "@Setter\|@Data" domain/src/main/java` returns zero matches, all 8 `@Builder` usages are `access = AccessLevel.PRIVATE`, and `javap -p` on `OutboxEvent` shows zero setters, a private constructor, a private `builder()` and exactly three public mutators.

- **Step 4 follow-up (fixes Phase 4 code):** the code-reviewer flagged `TicketType.confirmSale()` assigning `soldQuantity` before `transitionTo()` but dismissed it as unreachable, reasoning that `status == SOLD_OUT` implies zero remaining capacity. That invariant is maintained by the mutators but **not** by `reconstitute()`, which null-checks only by convention. A row stored as `SOLD_OUT` with `totalQuantity` raised outside the domain (admin SQL, migration, a bad mapper) loads fine, passes the capacity guard, increments the counter, then throws on the missing `SOLD_OUT -> SOLD_OUT` edge — a failed call over an already-mutated aggregate, surfaced as `TicketTypeNotAvailableException` with the message `cannot move from SOLD_OUT to SOLD_OUT`. Fixed by computing `newSold` first, transitioning under an added `status != SOLD_OUT` guard, and assigning last; the divergent sale now completes and restores `soldQuantity == totalQuantity`. `releaseSale()` reordered the same way (its edge is legal today, so this is prevention only). Four tests added to `TicketTypeTest` (25 -> 29) around a new `aDivergentSoldOutType(total, sold)` helper. Mutation-tested: restoring the old ordering failed exactly 1 of 189 (`confirmSale_onDivergentSoldOutRowCompletes:201`) with that exact exception. Reverted; suite green.

### Next immediate action
All 8 phases complete. The `--hard` review gate was approved on 2026-09-16. Cook Step 5 finalization completed: plan bookkeeping synced. User opted out of the automatic git-manager step; no commits were made by the pipeline. Domain module is ready for application-layer development.

**On the `SIMPLIFY_TRIGGERED` hook (Step 3.S):** investigated 2026-09-16 — it is a false positive and the earlier note here saying simplify was "owed" was wrong. The tracker
(`.claude/session-data/simplify-tracker-36434a72-*.json`) shows it fired on `fileCount >= 8` with `total_loc` 195/400 and a largest file of 77/200, and 7 of those 8 "files" are markdown plan/doc files plus `pom.xml` — only `DomainException.java` (15 lines) is Java. Cause: the repo has no `.ck.json`, so `sourceExtensions` is empty and `simplify_gate.py` skips its extension filter entirely (`if source_extensions and ...`), counting every file type. The tracker also froze at the moment it fired (2026-09-15 13:07), so none of the Phase 5–8 code is in it — running `simplify` "on files edited this phase" would not reach `Order`, `Ticket`, `Payment` or `OutboxEvent`. Awaiting the user's call between: delete the tracker and add `.ck.json` with `sourceExtensions: [".java"]` (recommended), or leave it. Step 4's `code-reviewer` reads real code and is unaffected either way.

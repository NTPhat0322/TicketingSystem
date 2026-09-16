# Phase 2: User Aggregate

## Requirements
Model the `User` aggregate root with the entity-construction and guarded-mutation pattern that every later phase will repeat: private builder + named static factory, no setters, identity-based equality, a repository port, and unit tests.

## Steps
1. Implement `UserRole` enum (`CUSTOMER`, `ORGANIZER`, `ADMIN`) — no transition table needed (role is a plain attribute here, not a lifecycle state machine).
2. Implement `User` entity mirroring the `users` table (minus persistence annotations): `id`, `email`, `phone`, `passwordHash`, `fullName`, `role`, `createdAt`, `updatedAt`.
3. Implement the static factory `User.register(...)` with field-blank validation.
4. Implement guarded mutation methods `changeRole(UserRole newRole)` and `updateProfile(String phone, String fullName)`.
5. Implement `InvalidUserDataException` for all validation failures in this aggregate.
6. Implement `UserRepository` port (`save`, `findById`, `findByEmail`, `existsByEmail`).
7. Write `UserTest` covering factory + both guarded methods, happy path and at least one failure each.
8. Run `mvn test -pl domain` and confirm all tests pass.

## Files to Create
- `domain/src/main/java/com/tienphat/domain/model/User.java`
- `domain/src/main/java/com/tienphat/domain/model/UserRole.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidUserDataException.java`
- `domain/src/main/java/com/tienphat/domain/repository/UserRepository.java`
- `domain/src/test/java/com/tienphat/domain/model/UserTest.java`

## Design Detail

**`User`** (`@Getter`, `@Builder(access = AccessLevel.PRIVATE)`, `@EqualsAndHashCode(of = "id")`):
- Fields: `UUID id` (final), `String email`, `String phone`, `String passwordHash`, `String fullName`, `UserRole role`, `Instant createdAt` (final), `Instant updatedAt`.
- `static User register(UUID id, String email, String phone, String passwordHash, String fullName, UserRole role)`: validates `email`, `passwordHash`, `fullName` non-blank and `role` non-null; throws `InvalidUserDataException` naming the offending field; sets `createdAt = updatedAt = Instant.now()`.
- `void changeRole(UserRole newRole)`: throws `InvalidUserDataException` if `newRole == null`; otherwise sets `role` and bumps `updatedAt`. No-op-safe if `newRole` equals current role (still bumps `updatedAt`, not an error).
- `void updateProfile(String phone, String fullName)`: throws `InvalidUserDataException` if `fullName` is blank; `phone` may be blank/null (optional field); bumps `updatedAt`.

**`UserRepository`**: `User save(User user)`, `Optional<User> findById(UUID id)`, `Optional<User> findByEmail(String email)`, `boolean existsByEmail(String email)`.

## Unit Tests (`UserTest`)
- `register()` succeeds and returns a `User` with the given fields and matching `createdAt`/`updatedAt`.
- `register()` throws `InvalidUserDataException` when `email` is blank.
- `register()` throws `InvalidUserDataException` when `role` is `null`.
- `changeRole()` succeeds and updates `role` + `updatedAt`.
- `changeRole()` throws `InvalidUserDataException` when passed `null`.
- `updateProfile()` succeeds and updates `phone`/`fullName`.
- `updateProfile()` throws `InvalidUserDataException` when `fullName` is blank.
- Two `User` instances built with the same `id` but different other fields are `equals()` (identity-based, not field-based).

## Success Criteria (Pass/Fail)
- `mvn test -pl domain` passes, including all `UserTest` cases above.
- `User` has no public setter and no public no-arg/all-args constructor exposed (`@Builder` is `PRIVATE`; only `register()` constructs).
- `User.equals()`/`hashCode()` verified as identity-based via the dedicated test case.

## Risks
- None specific beyond the shared conventions already covered in Phase 1's Risks.

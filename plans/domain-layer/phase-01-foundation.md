# Phase 1: Foundation

## Requirements
Establish the `domain` module's package skeleton, the shared `DomainException` base, the `Money` value object, and JUnit5 test infrastructure so every subsequent phase can build on a consistent, verified pattern.

## Steps
1. Add `junit-jupiter` and `assertj-core` as `test`-scope dependencies to `domain/pom.xml` (versions inherited from the root `spring-boot-starter-parent` BOM — no explicit `<version>` needed).
2. Create the empty package skeleton (`model/`, `vo/`, `exception/`, `repository/`, `port/`) under `domain/src/main/java/com/tienphat/domain/` and its test mirror under `domain/src/test/java/com/tienphat/domain/`.
3. Implement the abstract `DomainException` base exactly matching doc #2's pattern (message-only constructor, `abstract`).
4. Implement `Money`, the sole value object: wraps `BigDecimal`, scale 2, `HALF_UP` rounding, non-negative invariant, arithmetic helpers.
5. Implement `InvalidMoneyException` for negative/null-amount construction and subtraction-below-zero.
6. Write `MoneyTest` covering construction, arithmetic, and the invariant violation.
7. Run `mvn test -pl domain` and confirm the build compiles and the new test passes with zero other test files present yet.

## Files to Create
- `domain/pom.xml` (edit — add test dependencies)
- `domain/src/main/java/com/tienphat/domain/exception/DomainException.java`
- `domain/src/main/java/com/tienphat/domain/exception/InvalidMoneyException.java`
- `domain/src/main/java/com/tienphat/domain/vo/Money.java`
- `domain/src/test/java/com/tienphat/domain/vo/MoneyTest.java`

## Design Detail

**`DomainException`** — abstract, extends `RuntimeException`, single `protected DomainException(String message)` constructor. No fields, no subclass here beyond `InvalidMoneyException`.

**`Money`** (`@Value` Lombok — fully immutable, full-field equality):
- Field: `BigDecimal amount` (always stored at scale 2, `HALF_UP`).
- `static Money of(BigDecimal amount)` — throws `InvalidMoneyException` if `amount == null` or `amount.signum() < 0`.
- `static Money zero()` — convenience for `BigDecimal.ZERO`.
- `Money add(Money other)` — returns new `Money` with summed amount.
- `Money subtract(Money other)` — throws `InvalidMoneyException` if result would be negative.
- `Money multiply(int factor)` — throws `InvalidMoneyException` if `factor < 0`; used for `unitPrice * quantity`.
- `boolean isZero()`, `boolean isGreaterThanOrEqualTo(Money other)`.

## Unit Tests (`MoneyTest`)
- `of()` succeeds with a positive scale-2 amount.
- `of()` throws `InvalidMoneyException` for a negative amount.
- `of()` throws `InvalidMoneyException` for a `null` amount.
- `add()` sums two amounts correctly.
- `subtract()` succeeds when result is non-negative.
- `subtract()` throws `InvalidMoneyException` when result would go negative.
- `multiply(int)` scales correctly (e.g. `10.00 * 3 = 30.00`).
- Two `Money` instances with the same amount are `equals()` and share `hashCode()` (value semantics, not identity).

## Success Criteria (Pass/Fail)
- `mvn test -pl domain` compiles and passes with only `MoneyTest` present.
- `DomainException` is the only `abstract` class in `exception/`; `InvalidMoneyException` is concrete and instantiable.
- No `@Setter` or `@Data` appears anywhere in `domain/src/main/java`.
- `Money` has no public mutator; all arithmetic methods return a new instance.

## Risks
- Lombok annotation processor not enabled in some IDEs can produce false "cannot resolve method" errors for `@Value`-generated methods — mitigated by relying on `mvn test` (command-line, annotation processing always on) as the actual pass/fail source of truth, not the IDE.

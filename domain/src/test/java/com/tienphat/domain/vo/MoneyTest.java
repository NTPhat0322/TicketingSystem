package com.tienphat.domain.vo;

import com.tienphat.domain.exception.InvalidMoneyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("of() accepts a positive amount and normalises it to scale 2")
    void of_succeedsWithPositiveAmount() {
        Money money = Money.of(new BigDecimal("150000"));

        assertThat(money.getAmount()).isEqualByComparingTo("150000.00");
        assertThat(money.getAmount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("of() rejects a negative amount")
    void of_throwsOnNegativeAmount() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-0.01")))
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    @DisplayName("of() rejects a null amount")
    void of_throwsOnNullAmount() {
        assertThatThrownBy(() -> Money.of(null))
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    @DisplayName("zero() is zero and reports isZero()")
    void zero_isZero() {
        assertThat(Money.zero().isZero()).isTrue();
        assertThat(Money.of(new BigDecimal("0.01")).isZero()).isFalse();
    }

    @Test
    @DisplayName("add() sums two amounts")
    void add_sumsAmounts() {
        Money result = Money.of(new BigDecimal("150000")).add(Money.of(new BigDecimal("49999.50")));

        assertThat(result.getAmount()).isEqualByComparingTo("199999.50");
    }

    @Test
    @DisplayName("subtract() succeeds when the result stays non-negative")
    void subtract_succeedsWhenNonNegative() {
        Money result = Money.of(new BigDecimal("150000")).subtract(Money.of(new BigDecimal("50000")));

        assertThat(result.getAmount()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("subtract() rejects a result that would go negative")
    void subtract_throwsWhenResultWouldBeNegative() {
        Money small = Money.of(new BigDecimal("100"));
        Money large = Money.of(new BigDecimal("100.01"));

        assertThatThrownBy(() -> small.subtract(large))
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    @DisplayName("subtract() down to exactly zero is allowed")
    void subtract_allowsExactZero() {
        Money result = Money.of(new BigDecimal("100")).subtract(Money.of(new BigDecimal("100")));

        assertThat(result.isZero()).isTrue();
    }

    @Test
    @DisplayName("multiply() scales the amount by an integer factor")
    void multiply_scalesAmount() {
        Money result = Money.of(new BigDecimal("10.00")).multiply(3);

        assertThat(result.getAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("multiply() by zero yields zero")
    void multiply_byZeroYieldsZero() {
        assertThat(Money.of(new BigDecimal("10.00")).multiply(0).isZero()).isTrue();
    }

    @Test
    @DisplayName("multiply() rejects a negative factor")
    void multiply_throwsOnNegativeFactor() {
        Money money = Money.of(new BigDecimal("10.00"));

        assertThatThrownBy(() -> money.multiply(-1))
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    @DisplayName("isGreaterThanOrEqualTo() compares by amount, including equality")
    void isGreaterThanOrEqualTo_comparesByAmount() {
        Money hundred = Money.of(new BigDecimal("100"));
        Money fifty = Money.of(new BigDecimal("50"));

        assertThat(hundred.isGreaterThanOrEqualTo(fifty)).isTrue();
        assertThat(hundred.isGreaterThanOrEqualTo(hundred)).isTrue();
        assertThat(fifty.isGreaterThanOrEqualTo(hundred)).isFalse();
    }

    @Test
    @DisplayName("equal amounts are equal and share a hashCode, regardless of input scale")
    void equality_isByValueNotIdentity() {
        Money fromWholeNumber = Money.of(new BigDecimal("100"));
        Money fromScaledNumber = Money.of(new BigDecimal("100.00"));

        assertThat(fromWholeNumber).isEqualTo(fromScaledNumber);
        assertThat(fromWholeNumber).hasSameHashCodeAs(fromScaledNumber);
    }

    @Test
    @DisplayName("arithmetic returns new instances, leaving the operands untouched")
    void arithmetic_isNonMutating() {
        Money original = Money.of(new BigDecimal("100"));

        original.add(Money.of(new BigDecimal("50")));
        original.multiply(5);

        assertThat(original.getAmount()).isEqualByComparingTo("100.00");
    }
}

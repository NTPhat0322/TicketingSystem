package com.tienphat.domain.vo;

import com.tienphat.domain.exception.InvalidMoneyException;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary amount, always non-negative and normalised to scale 2.
 *
 * <p>Single-currency system (VND), so there is deliberately no currency field — adding one
 * later is a breaking change by design, to force a decision rather than let mixed currencies
 * silently add up.
 *
 * <p>{@code @Value} rather than the entities' {@code @Getter}/{@code @Builder} pattern: a value
 * object has no identity, so equality is over all fields and every instance is immutable.
 */
@Value
public class Money {

    BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount;
    }

    public static Money of(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidMoneyException("Money amount must not be null");
        }
        if (amount.signum() < 0) {
            throw new InvalidMoneyException("Money amount must not be negative, but was " + amount);
        }
        return new Money(normalise(amount));
    }

    public static Money zero() {
        return new Money(normalise(BigDecimal.ZERO));
    }

    public Money add(Money other) {
        return new Money(normalise(this.amount.add(other.amount)));
    }

    /**
     * @throws InvalidMoneyException if the result would be negative — money cannot go below zero,
     *                               so an over-subtraction is a bug at the call site, not a value
     *                               to be clamped silently.
     */
    public Money subtract(Money other) {
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new InvalidMoneyException(
                    "Subtracting " + other.amount + " from " + this.amount + " would yield a negative amount");
        }
        return new Money(normalise(result));
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new InvalidMoneyException("Money multiplier must not be negative, but was " + factor);
        }
        return new Money(normalise(this.amount.multiply(BigDecimal.valueOf(factor))));
    }

    public boolean isZero() {
        return this.amount.signum() == 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        return this.amount.compareTo(other.amount) >= 0;
    }

    /**
     * Scale 2 with HALF_UP so that equal amounts always have an equal {@link BigDecimal}
     * representation — {@code @Value}'s equals delegates to {@code BigDecimal.equals}, which is
     * scale-sensitive ({@code 10.0} != {@code 10.00}). Normalising on every construction is what
     * makes value equality behave the way callers expect.
     */
    private static BigDecimal normalise(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

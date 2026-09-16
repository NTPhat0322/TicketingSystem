package com.tienphat.domain.exception;

/**
 * A second payment was applied to an order that is already {@code PAID}.
 *
 * <p>The domain half of a two-layer idempotency guard. The other half is the unique constraint on
 * {@code payments.transaction_ref} (design doc §4): the constraint stops the same gateway callback
 * being recorded twice, this stops two <em>different</em> successful charges landing on one order.
 * Neither subsumes the other.
 *
 * <p>Deliberately distinct from {@link InvalidOrderStateException}: a caller that retries a webhook
 * wants to treat this as "already done, carry on", while a genuine illegal transition is an error.
 * Phase 7's payment flow reuses this class unchanged.
 */
public class DuplicatePaymentException extends DomainException {

    public DuplicatePaymentException(String message) {
        super(message);
    }
}

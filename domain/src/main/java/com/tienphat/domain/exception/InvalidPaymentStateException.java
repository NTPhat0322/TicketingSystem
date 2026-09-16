package com.tienphat.domain.exception;

/**
 * A payment was asked for a transition its current status does not allow — succeeding an attempt
 * that already failed, or failing one that already collected money.
 *
 * <p>Deliberately not raised for a replayed success callback; that is
 * {@link DuplicatePaymentException}, which the webhook handler treats as routine.
 */
public class InvalidPaymentStateException extends DomainException {

    public InvalidPaymentStateException(String message) {
        super(message);
    }
}

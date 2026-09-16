package com.tienphat.domain.exception;

/**
 * An order was asked for a transition its current status does not allow — paying an expired order,
 * expiring a paid one, adding an item after checkout.
 */
public class InvalidOrderStateException extends DomainException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}

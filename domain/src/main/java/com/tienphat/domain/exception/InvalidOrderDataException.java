package com.tienphat.domain.exception;

/**
 * An order was created or reconstituted with a missing, blank, or non-positive required field.
 *
 * <p>Not listed in phase-05 — added for the same reason as {@code InvalidEventDataException}:
 * {@code create()} must reject a null {@code userId} and {@code reconstitute()} must reject a null
 * column, and neither is a state-machine or line-item error.
 */
public class InvalidOrderDataException extends DomainException {

    public InvalidOrderDataException(String message) {
        super(message);
    }
}

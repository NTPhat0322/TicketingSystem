package com.tienphat.domain.exception;

/**
 * An outbox row was built without something the publisher needs: a null aggregate reference, an
 * empty payload, or an event type recorded against an aggregate type it cannot belong to.
 *
 * <p>Raised at write time, in the same transaction as the business change that triggered it, so a
 * malformed row never reaches the poller.
 */
public class InvalidOutboxEventDataException extends DomainException {

    public InvalidOutboxEventDataException(String message) {
        super(message);
    }
}

package com.tienphat.domain.exception;

/**
 * An outbox row was asked for a transition its current status does not allow — publishing a row that
 * already failed, or retrying one that was never attempted.
 *
 * <p>Notably raised when a PUBLISHED row is published again. Unlike a replayed payment callback,
 * this is not routine traffic with its own exception type: the poller owns both sides of the loop,
 * so a second publish of the same row means the poller lost track of its own work.
 */
public class InvalidOutboxEventStateException extends DomainException {

    public InvalidOutboxEventStateException(String message) {
        super(message);
    }
}

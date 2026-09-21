package com.tienphat.domain.exception;

/** A {@code TicketType} was saved against a stale {@code version} — another edit won the race. */
public class TicketTypeConcurrentUpdateException extends DomainException {

    public TicketTypeConcurrentUpdateException(String message) {
        super(message);
    }
}

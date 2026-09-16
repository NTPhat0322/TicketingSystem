package com.tienphat.domain.exception;

/** The ticket type is {@code CLOSED} and can no longer be sold or closed again. */
public class TicketTypeNotAvailableException extends DomainException {

    public TicketTypeNotAvailableException(String message) {
        super(message);
    }
}

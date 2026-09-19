package com.tienphat.domain.exception;

/** No {@code TicketType} exists with the requested id. */
public class TicketTypeNotFoundException extends DomainException {

    public TicketTypeNotFoundException(String message) {
        super(message);
    }
}

package com.tienphat.domain.exception;

/** No live inventory remains for the requested ticket type. */
public class TicketTypeSoldOutException extends DomainException {

    public TicketTypeSoldOutException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.CONFLICT;
    }
}

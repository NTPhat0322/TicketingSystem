package com.tienphat.domain.exception;

/** An event is not in its explicit ON_SALE lifecycle state. */
public class EventNotOnSaleException extends DomainException {

    public EventNotOnSaleException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.CONFLICT;
    }
}

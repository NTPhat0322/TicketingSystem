package com.tienphat.domain.exception;

/** The sale-time stock key has not been prepared, so the reservation must fail closed. */
public class StockNotWarmedException extends DomainException {

    public StockNotWarmedException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.CONFLICT;
    }
}

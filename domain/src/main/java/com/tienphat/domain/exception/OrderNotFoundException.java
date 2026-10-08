package com.tienphat.domain.exception;

/** No persisted Order or recoverable reservation intent exists for the requested id. */
public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.NOT_FOUND;
    }
}

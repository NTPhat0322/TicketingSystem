package com.tienphat.domain.exception;

/** An event was asked to make a transition its current status does not allow. */
public class InvalidEventStateException extends DomainException {

    public InvalidEventStateException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.VALIDATION;
    }
}

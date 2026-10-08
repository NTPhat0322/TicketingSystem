package com.tienphat.domain.exception;

/** The requested quantity would exceed the user's configured reservation limit. */
public class ReservationLimitExceededException extends DomainException {

    public ReservationLimitExceededException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.CONFLICT;
    }
}

package com.tienphat.domain.exception;

/** The live reservation dependency is unavailable, so the API must fail closed. */
public class ReservationServiceUnavailableException extends DomainException {

    public ReservationServiceUnavailableException(String message) {
        super(message);
    }

    public ReservationServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.SERVICE_UNAVAILABLE;
    }
}

package com.tienphat.domain.exception;

/** A reservation request is missing identity/snapshot data or contains an invalid time window. */
public class InvalidReservationRequestException extends DomainException {

    public InvalidReservationRequestException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.VALIDATION;
    }
}

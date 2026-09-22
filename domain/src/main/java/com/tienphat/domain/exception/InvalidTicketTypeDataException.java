package com.tienphat.domain.exception;

/** A ticket type was created with a missing, blank, or non-positive required field. */
public class InvalidTicketTypeDataException extends DomainException {

    public InvalidTicketTypeDataException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.VALIDATION;
    }
}

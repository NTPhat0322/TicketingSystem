package com.tienphat.domain.exception;

/** A ticket was issued or reconstituted with a missing or blank required field. */
public class InvalidTicketDataException extends DomainException {

    public InvalidTicketDataException(String message) {
        super(message);
    }
}

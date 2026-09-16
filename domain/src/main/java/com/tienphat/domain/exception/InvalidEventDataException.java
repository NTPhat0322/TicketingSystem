package com.tienphat.domain.exception;

/** An event was created with a missing or blank required field. */
public class InvalidEventDataException extends DomainException {

    public InvalidEventDataException(String message) {
        super(message);
    }
}

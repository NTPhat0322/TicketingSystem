package com.tienphat.domain.exception;

/** An event's time fields are ordered in a way that cannot describe a real sale. */
public class InvalidEventScheduleException extends DomainException {

    public InvalidEventScheduleException(String message) {
        super(message);
    }
}

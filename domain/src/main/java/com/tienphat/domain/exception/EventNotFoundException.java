package com.tienphat.domain.exception;

/** No {@code Event} exists with the requested id. */
public class EventNotFoundException extends DomainException {

    public EventNotFoundException(String message) {
        super(message);
    }
}

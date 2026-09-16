package com.tienphat.domain.exception;

/** An order line was built with a non-positive quantity or a missing required field. */
public class InvalidOrderItemException extends DomainException {

    public InvalidOrderItemException(String message) {
        super(message);
    }
}

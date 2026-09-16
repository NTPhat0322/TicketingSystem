package com.tienphat.domain.exception;

/** A sale quantity was non-positive, or a release would drive the sold count below zero. */
public class InvalidReservationQuantityException extends DomainException {

    public InvalidReservationQuantityException(String message) {
        super(message);
    }
}

package com.tienphat.domain.exception;

/**
 * A payment was initiated or reconstituted with a missing, blank, or zero-amount required field.
 */
public class InvalidPaymentDataException extends DomainException {

    public InvalidPaymentDataException(String message) {
        super(message);
    }
}

package com.tienphat.domain.exception;

/** No payment attempt exists for the supplied transaction reference. */
public class PaymentNotFoundException extends DomainException {

    public PaymentNotFoundException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.NOT_FOUND;
    }
}

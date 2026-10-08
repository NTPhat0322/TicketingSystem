package com.tienphat.domain.exception;

/** A payment initiation or callback cannot be applied to the current persisted state. */
public class PaymentOperationRejectedException extends DomainException {

    public PaymentOperationRejectedException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.CONFLICT;
    }
}

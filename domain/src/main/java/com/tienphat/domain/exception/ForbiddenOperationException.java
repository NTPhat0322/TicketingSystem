package com.tienphat.domain.exception;

/** The authenticated caller is not allowed to perform the requested operation. */
public class ForbiddenOperationException extends DomainException {

    public ForbiddenOperationException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.FORBIDDEN;
    }
}

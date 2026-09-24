package com.tienphat.domain.exception;

/** No {@code User} exists with the requested id or email. */
public class UserNotFoundException extends DomainException {

    public UserNotFoundException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.NOT_FOUND;
    }
}

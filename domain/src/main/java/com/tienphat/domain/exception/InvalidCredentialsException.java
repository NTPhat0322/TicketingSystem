package com.tienphat.domain.exception;

/**
 * Login failed — either the email is unknown or the password is wrong.
 *
 * <p>The message is deliberately identical for both causes (no email-enumeration signal); callers
 * in {@code application.auth} must never vary the message by which case actually applied.
 */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.UNAUTHORIZED;
    }
}

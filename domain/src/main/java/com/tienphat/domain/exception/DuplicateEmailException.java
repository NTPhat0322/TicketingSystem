package com.tienphat.domain.exception;

/**
 * A second {@code User} was registered with an email that already exists.
 *
 * <p>The domain half of a two-layer guard against duplicate accounts: the unique constraint on
 * {@code users.email} is the other half, and is what actually stops two concurrent registrations
 * for the same email both succeeding — this exception is how that constraint violation, or a
 * plain pre-check, surfaces to the caller as a single, consistent 409.
 */
public class DuplicateEmailException extends DomainException {

    public DuplicateEmailException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.CONFLICT;
    }
}

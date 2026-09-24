package com.tienphat.domain.exception;

/**
 * The presented refresh token could not be used to issue a new session.
 *
 * <p>Covers unknown, expired, and revoked tokens uniformly — one message shape, no signal about
 * which case applied.
 */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }

    @Override
    public ErrorType errorType() {
        return ErrorType.UNAUTHORIZED;
    }
}

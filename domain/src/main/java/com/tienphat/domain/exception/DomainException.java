package com.tienphat.domain.exception;

/**
 * Base type for every business-rule violation raised by the domain module.
 *
 * <p>Unchecked on purpose: a violated invariant is a programming or workflow error, not a
 * condition the caller is expected to recover from inline. Abstract so that callers always
 * catch a meaningful subtype rather than a catch-all.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}

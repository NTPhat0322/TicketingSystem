package com.tienphat.domain.exception;

/** Classifies a {@link DomainException} for translation to a transport-layer status, without the domain depending on any transport type itself. */
public enum ErrorType {
    VALIDATION,
    NOT_FOUND,
    CONFLICT,
    INTERNAL
}

package com.tienphat.application.order;

/** Result of the locked PostgreSQL part of an expiry attempt. */
public enum ExpiryOutcome {
    EXPIRED,
    ALREADY_EXPIRED,
    PAID,
    OTHER_TERMINAL,
    ORDER_MISSING,
    NOT_DUE
}

package com.tienphat.domain.port;

/**
 * Recovery state of a reservation intent outside the PostgreSQL Order aggregate.
 *
 * <p>The first implementation stores this value in Redis. It deliberately lives in the
 * adapter-neutral port package so relay, status polling, and reconciliation can share the same
 * vocabulary without importing Redis or Jackson.
 */
public enum ReservationIntentState {

    PENDING,
    ENQUEUED,
    ORDER_CREATED,
    EXPIRED,
    RELEASED,
    COMPLETED;

    public boolean isTerminal() {
        return this == EXPIRED || this == RELEASED || this == COMPLETED;
    }
}

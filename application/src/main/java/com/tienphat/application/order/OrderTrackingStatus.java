package com.tienphat.application.order;

import com.tienphat.domain.model.OrderStatus;

/**
 * Client-facing lifecycle for an order lookup.
 *
 * <p>{@code CREATING} is intentionally application-level: it exists while the Redis reservation
 * has succeeded but the asynchronous worker has not inserted the PostgreSQL Order yet.
 */
public enum OrderTrackingStatus {

    CREATING,
    PENDING_PAYMENT,
    PAID,
    EXPIRED,
    CANCELLED,
    FAILED;

    public static OrderTrackingStatus from(OrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT -> PENDING_PAYMENT;
            case PAID -> PAID;
            case EXPIRED -> EXPIRED;
            case CANCELLED -> CANCELLED;
            case FAILED -> FAILED;
        };
    }
}

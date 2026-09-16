package com.tienphat.domain.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of an {@link Order}. {@code PENDING_PAYMENT} is the only non-terminal status: an order
 * is settled exactly once, in one of four ways, and never reopens.
 *
 * <p>{@code EXPIRED} is the hold timer running out, {@code CANCELLED} is the buyer walking away,
 * {@code FAILED} is the payment gateway rejecting the charge. They are distinct because the
 * compensating action differs — only {@code FAILED} follows a real payment attempt.
 */
public enum OrderStatus {

    PENDING_PAYMENT,
    PAID,
    EXPIRED,
    CANCELLED,
    FAILED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(PENDING_PAYMENT, Set.of(PAID, EXPIRED, CANCELLED, FAILED));
        TRANSITIONS.put(PAID, Set.of());
        TRANSITIONS.put(EXPIRED, Set.of());
        TRANSITIONS.put(CANCELLED, Set.of());
        TRANSITIONS.put(FAILED, Set.of());
    }

    public boolean canTransitionTo(OrderStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }
}

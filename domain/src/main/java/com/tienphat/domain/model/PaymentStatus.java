package com.tienphat.domain.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a {@link Payment} attempt. One attempt settles once: {@code PENDING} is the only
 * status with outgoing transitions.
 *
 * <p>{@code FAILED} is terminal — there is no {@code FAILED → PENDING} retry edge. Design doc §4
 * specifies no retry flow, and {@code payments.transaction_ref} is unique, so a genuine retry is a
 * new attempt with a new reference rather than the old row changing its mind. Recorded as an
 * assumption in phase-07 Risks; reopening it is a domain change, not a call-site workaround.
 */
public enum PaymentStatus {

    PENDING,
    SUCCESS,
    FAILED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        TRANSITIONS.put(PENDING, Set.of(SUCCESS, FAILED));
        TRANSITIONS.put(SUCCESS, Set.of());
        TRANSITIONS.put(FAILED, Set.of());
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }
}

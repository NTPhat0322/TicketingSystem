package com.tienphat.domain.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a {@link Ticket}. A ticket is admitted once or voided once, never both and never
 * twice — so {@code ISSUED} is the only status with outgoing transitions.
 *
 * <p>There is deliberately no {@code CHECKED_IN → ISSUED} edge. Undoing an admission would mean the
 * same QR code can pass the gate again, which is the one thing a ticket must not allow; a mistaken
 * scan is resolved by staff, not by the domain rewinding its own record.
 */
public enum TicketStatus {

    ISSUED,
    CHECKED_IN,
    CANCELLED;

    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = new EnumMap<>(TicketStatus.class);

    static {
        TRANSITIONS.put(ISSUED, Set.of(CHECKED_IN, CANCELLED));
        TRANSITIONS.put(CHECKED_IN, Set.of());
        TRANSITIONS.put(CANCELLED, Set.of());
    }

    public boolean canTransitionTo(TicketStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }
}

package com.tienphat.domain.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a {@link TicketType}.
 *
 * <p>{@code SOLD_OUT → ACTIVE} is not a restock — nothing raises {@code totalQuantity}. It is
 * capacity reopened by a refund or chargeback via {@code TicketType.releaseSale}. {@code CLOSED} is
 * terminal: the organizer stopped selling this tier.
 */
public enum TicketTypeStatus {

    ACTIVE,
    SOLD_OUT,
    CLOSED;

    private static final Map<TicketTypeStatus, Set<TicketTypeStatus>> TRANSITIONS =
            new EnumMap<>(TicketTypeStatus.class);

    static {
        TRANSITIONS.put(ACTIVE, Set.of(SOLD_OUT, CLOSED));
        TRANSITIONS.put(SOLD_OUT, Set.of(ACTIVE, CLOSED));
        TRANSITIONS.put(CLOSED, Set.of());
    }

    public boolean canTransitionTo(TicketTypeStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }
}

package com.tienphat.domain.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of an {@link Event}.
 *
 * <p>Unlike {@link UserRole}, which is a plain attribute, this enum owns a transition table: an
 * event may only move along the edges declared here. {@code CLOSED} and {@code CANCELLED} are
 * terminal — an event that ended or was called off never sells again.
 *
 * <p>The table is the single source of truth. {@code Event}'s guarded methods consult it rather
 * than re-stating the rule, so a new status cannot be added without deciding where it connects.
 */
public enum EventStatus {

    DRAFT,
    PUBLISHED,
    ON_SALE,
    CLOSED,
    CANCELLED;

    private static final Map<EventStatus, Set<EventStatus>> TRANSITIONS = new EnumMap<>(EventStatus.class);

    static {
        TRANSITIONS.put(DRAFT, Set.of(PUBLISHED, CANCELLED));
        TRANSITIONS.put(PUBLISHED, Set.of(ON_SALE, CANCELLED));
        TRANSITIONS.put(ON_SALE, Set.of(CLOSED, CANCELLED));
        TRANSITIONS.put(CLOSED, Set.of());
        TRANSITIONS.put(CANCELLED, Set.of());
    }

    public boolean canTransitionTo(EventStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }
}

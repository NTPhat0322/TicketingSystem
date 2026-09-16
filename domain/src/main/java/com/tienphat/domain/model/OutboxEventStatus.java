package com.tienphat.domain.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of an {@link OutboxEvent} row as the publisher poller works through it.
 *
 * <p>The first status machine in this domain that is <em>not</em> a one-way trip: {@code FAILED} can
 * return to {@code PENDING}. That edge is the retry loop — a publish failure is usually the broker
 * being briefly unavailable, not the event being wrong, so the row goes back in the queue rather
 * than being abandoned.
 *
 * <p>{@code PUBLISHED} is terminal, and deliberately so. Re-publishing a row that already reached
 * the broker is exactly the duplicate delivery the outbox pattern exists to bound (design doc §2.5);
 * consumers must still be idempotent, but the domain will not manufacture duplicates on its own.
 */
public enum OutboxEventStatus {

    PENDING,
    PUBLISHED,
    FAILED;

    private static final Map<OutboxEventStatus, Set<OutboxEventStatus>> TRANSITIONS =
            new EnumMap<>(OutboxEventStatus.class);

    static {
        TRANSITIONS.put(PENDING, Set.of(PUBLISHED, FAILED));
        TRANSITIONS.put(PUBLISHED, Set.of());
        TRANSITIONS.put(FAILED, Set.of(PENDING));
    }

    public boolean canTransitionTo(OutboxEventStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }
}

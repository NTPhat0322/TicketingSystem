package com.tienphat.domain.model;

/**
 * Which aggregate an {@link OutboxEvent} is about.
 *
 * <p>Matches the {@code outbox_events.aggregate_type} values design doc §4 lists. It exists so the
 * publisher can route or filter without parsing {@code payload}: the row stays an opaque JSON
 * string to the domain, but its subject does not have to be.
 */
public enum AggregateType {

    ORDER,
    PAYMENT
}

package com.tienphat.domain.repository;

import com.tienphat.domain.model.OutboxEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link OutboxEvent} persistence. Owned by the domain, implemented in
 * {@code infrastructure}.
 *
 * <p>This port is why no {@code OrderEventPublisherPort} exists (design doc §3): the domain never
 * talks to the broker, it writes a row and lets the publisher job do the talking.
 *
 * <p>{@code findAllPending} is the poller's query. Two notes for whoever implements it: the
 * transaction that records an event must have committed before the row is visible here, and if more
 * than one publisher instance runs, the adapter needs row-level locking
 * ({@code FOR UPDATE SKIP LOCKED}) or two pollers will publish the same row. Neither belongs in the
 * domain, but both are load-bearing for the guarantee this interface implies.
 */
public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    Optional<OutboxEvent> findById(UUID id);

    List<OutboxEvent> findAllPending();
}

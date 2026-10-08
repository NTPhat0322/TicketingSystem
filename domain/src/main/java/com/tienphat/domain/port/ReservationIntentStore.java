package com.tienphat.domain.port;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Recovery-oriented operations for the reservation intent created beside a Redis hold.
 *
 * <p>The intent payload is deliberately exposed through a port rather than through a Redis type.
 * A relay can therefore read due work, claim it briefly, and make its delivery state durable
 * without coupling the application layer to Hashes or Sorted Sets.
 */
public interface ReservationIntentStore extends ReservationIntentStatusPort {

    /**
     * Returns at most {@code limit} intents whose retry time is due. The implementation must use
     * the pending index only to find ids and load the complete payload from the intent record.
     */
    List<ReservationIntent> findDue(Instant now, int limit);

    /** Returns enqueued intents whose Order has had longer than the configured grace period to appear. */
    List<ReservationIntent> findStaleEnqueued(Instant now, Duration gracePeriod, int limit);

    /** Returns non-completed intents whose captured hold deadline has passed. */
    List<ReservationIntent> findExpired(Instant now, int limit);

    /**
     * Claims one intent for a short period so multiple relay instances do not work on it at once.
     * The lease is only an optimisation; duplicate delivery remains safe by design.
     */
    boolean tryClaim(UUID orderId, String owner, Duration lease);

    /** Releases a claim only when it is still owned by {@code owner}. */
    void releaseClaim(UUID orderId, String owner);

    /** Reschedules a failed publish and keeps the intent discoverable. */
    void reschedule(UUID orderId, int retryCount, Instant nextRetryAt);

    /** Marks a publisher-confirmed message as enqueued and removes its pending index entry. */
    void markEnqueued(UUID orderId);

    /** Marks the Order worker's durable creation and removes any stale pending index entry. */
    void markOrderCreated(UUID orderId);

    /** Marks a paid/settled reservation complete so reconciliation no longer treats it as a hold. */
    void markCompleted(UUID orderId);

    /** Marks a terminal expiry decision and removes the intent from the delivery index. */
    void markExpired(UUID orderId);
}

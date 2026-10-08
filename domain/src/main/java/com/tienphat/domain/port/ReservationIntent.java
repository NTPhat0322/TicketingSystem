package com.tienphat.domain.port;

import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.vo.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable read model for a pending reservation intent.
 *
 * <p>The Hash is the source of the payload; the Sorted Set index only carries {@code orderId} and
 * {@code nextRetryAt}. Retry fields are included here because polling and reconciliation must be
 * able to inspect the same record without knowing the Redis representation.
 */
public record ReservationIntent(
        UUID orderId,
        UUID userId,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        Money unitPrice,
        int maxPerUser,
        int holdDurationSec,
        Instant reservedAt,
        Instant expiresAt,
        ReservationIntentState state,
        int retryCount,
        Instant nextRetryAt,
        Instant enqueuedAt) {

    public ReservationIntent(
            UUID orderId,
            UUID userId,
            UUID eventId,
            UUID ticketTypeId,
            int quantity,
            Money unitPrice,
            int maxPerUser,
            int holdDurationSec,
            Instant reservedAt,
            Instant expiresAt,
            ReservationIntentState state,
            int retryCount,
            Instant nextRetryAt) {
        this(orderId, userId, eventId, ticketTypeId, quantity, unitPrice, maxPerUser, holdDurationSec,
                reservedAt, expiresAt, state, retryCount, nextRetryAt, null);
    }

    public ReservationIntent {
        new ReservationRequest(orderId, userId, eventId, ticketTypeId, quantity, unitPrice,
                maxPerUser, holdDurationSec, reservedAt, expiresAt);
        if (state == null) {
            throw new InvalidReservationRequestException("Reservation intent state must not be null");
        }
        if (retryCount < 0) {
            throw new InvalidReservationRequestException(
                    "Reservation intent retryCount must not be negative");
        }
        if (nextRetryAt == null) {
            throw new InvalidReservationRequestException(
                    "Reservation intent nextRetryAt must not be null");
        }
    }
}

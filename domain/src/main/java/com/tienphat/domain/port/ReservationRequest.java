package com.tienphat.domain.port;

import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.exception.InvalidReservationQuantityException;
import com.tienphat.domain.vo.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot sent to the atomic reservation boundary.
 *
 * <p>It contains everything Redis must write in one Lua execution. In particular, the price and
 * expiry are captured before the call so later TicketType edits cannot change an accepted hold.
 */
public record ReservationRequest(
        UUID orderId,
        UUID userId,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        Money unitPrice,
        int maxPerUser,
        int holdDurationSec,
        Instant reservedAt,
        Instant expiresAt) {

    public ReservationRequest {
        require(orderId, "orderId");
        if (orderId.version() != 7) {
            throw new InvalidReservationRequestException("Reservation orderId must be a UUIDv7");
        }
        require(userId, "userId");
        require(eventId, "eventId");
        require(ticketTypeId, "ticketTypeId");
        if (quantity <= 0) {
            throw new InvalidReservationQuantityException(
                    "Reservation quantity must be positive, but was " + quantity);
        }
        if (unitPrice == null) {
            throw new InvalidReservationRequestException("Reservation unitPrice must not be null");
        }
        if (maxPerUser <= 0) {
            throw new InvalidReservationRequestException(
                    "Reservation maxPerUser must be positive, but was " + maxPerUser);
        }
        if (holdDurationSec <= 0) {
            throw new InvalidReservationRequestException(
                    "Reservation holdDurationSec must be positive, but was " + holdDurationSec);
        }
        require(reservedAt, "reservedAt");
        require(expiresAt, "expiresAt");
        if (!reservedAt.isBefore(expiresAt)) {
            throw new InvalidReservationRequestException(
                    "Reservation reservedAt must be before expiresAt");
        }
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidReservationRequestException(
                    "Reservation " + fieldName + " must not be null");
        }
    }
}

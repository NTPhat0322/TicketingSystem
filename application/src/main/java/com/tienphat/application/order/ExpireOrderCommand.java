package com.tienphat.application.order;

import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.vo.Money;

import java.time.Instant;
import java.util.UUID;

/** Snapshot carried by an expiry signal so the worker never trusts mutable TicketType data. */
public record ExpireOrderCommand(
        UUID messageId,
        UUID orderId,
        UUID userId,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        Money unitPrice,
        Instant reservedAt,
        Instant expiresAt) {

    public ExpireOrderCommand {
        require(messageId, "messageId");
        require(orderId, "orderId");
        require(userId, "userId");
        require(eventId, "eventId");
        require(ticketTypeId, "ticketTypeId");
        require(unitPrice, "unitPrice");
        require(reservedAt, "reservedAt");
        require(expiresAt, "expiresAt");
        if (quantity <= 0) {
            throw new InvalidReservationRequestException(
                    "Order expiry quantity must be positive, but was " + quantity);
        }
        if (!reservedAt.isBefore(expiresAt)) {
            throw new InvalidReservationRequestException(
                    "Order expiry reservedAt must be before expiresAt");
        }
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidReservationRequestException(
                    "Order expiry " + fieldName + " must not be null");
        }
    }
}

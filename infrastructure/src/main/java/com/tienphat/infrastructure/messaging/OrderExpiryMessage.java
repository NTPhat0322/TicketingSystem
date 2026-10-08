package com.tienphat.infrastructure.messaging;

import com.tienphat.application.order.ExpireOrderCommand;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.vo.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Versioned message sent to the per-message TTL queue and later dead-lettered for expiry. */
public record OrderExpiryMessage(
        int schemaVersion,
        UUID messageId,
        UUID orderId,
        UUID userId,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        BigDecimal unitPrice,
        String reservedAt,
        String expiresAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public OrderExpiryMessage {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported order-expiry schema version: " + schemaVersion);
        }
        require(messageId, "messageId");
        require(orderId, "orderId");
        require(userId, "userId");
        require(eventId, "eventId");
        require(ticketTypeId, "ticketTypeId");
        require(unitPrice, "unitPrice");
        require(reservedAt, "reservedAt");
        require(expiresAt, "expiresAt");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order-expiry quantity must be positive");
        }
    }

    public static OrderExpiryMessage fromIntent(ReservationIntent intent) {
        return new OrderExpiryMessage(
                CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                intent.orderId(),
                intent.userId(),
                intent.eventId(),
                intent.ticketTypeId(),
                intent.quantity(),
                intent.unitPrice().getAmount(),
                intent.reservedAt().toString(),
                intent.expiresAt().toString());
    }

    public ExpireOrderCommand toCommand() {
        return new ExpireOrderCommand(
                messageId,
                orderId,
                userId,
                eventId,
                ticketTypeId,
                quantity,
                Money.of(unitPrice),
                Instant.parse(reservedAt),
                Instant.parse(expiresAt));
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Order-expiry " + fieldName + " must not be null");
        }
    }
}

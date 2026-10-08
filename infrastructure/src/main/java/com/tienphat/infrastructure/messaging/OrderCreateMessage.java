package com.tienphat.infrastructure.messaging;

import com.tienphat.application.order.CreateOrderFromReservationCommand;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.vo.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Versioned wire contract for the durable Redis-reservation to Order handoff. */
public record OrderCreateMessage(
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

    public OrderCreateMessage {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported order-create schema version: " + schemaVersion);
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
            throw new IllegalArgumentException("Order-create quantity must be positive");
        }
    }

    public static OrderCreateMessage fromIntent(ReservationIntent intent) {
        return new OrderCreateMessage(
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

    public CreateOrderFromReservationCommand toCommand() {
        return new CreateOrderFromReservationCommand(
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
            throw new IllegalArgumentException("Order-create " + fieldName + " must not be null");
        }
    }
}

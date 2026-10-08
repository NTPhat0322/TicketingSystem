package com.tienphat.application.reservation;

import com.tienphat.application.order.OrderTrackingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Result returned immediately after the atomic reservation boundary succeeds. */
public record ReserveTicketResult(
        UUID orderId,
        UUID userId,
        OrderTrackingStatus status,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        BigDecimal unitPrice,
        Instant reservedAt,
        Instant expiresAt) {
}

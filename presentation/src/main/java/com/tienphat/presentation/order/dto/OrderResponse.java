package com.tienphat.presentation.order.dto;

import com.tienphat.application.order.OrderTrackingStatus;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Common response for the immediate CREATING response and later polling states. */
public record OrderResponse(
        UUID orderId,
        OrderTrackingStatus status,
        UUID userId,
        UUID eventId,
        UUID ticketTypeId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        Instant reservedAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        PaymentProvider paymentProvider,
        String paymentTransactionRef,
        PaymentStatus paymentStatus) {
}

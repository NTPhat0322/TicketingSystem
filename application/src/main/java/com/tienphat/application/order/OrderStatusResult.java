package com.tienphat.application.order;

import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderItem;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.PaymentStatus;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.application.reservation.ReserveTicketResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Common polling result for a Redis-only CREATING reservation and a persisted Order. */
public record OrderStatusResult(
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

    public static OrderStatusResult fromIntent(ReservationIntent intent, OrderTrackingStatus status) {
        BigDecimal totalAmount = intent.unitPrice().multiply(intent.quantity()).getAmount();
        return new OrderStatusResult(
                intent.orderId(),
                status,
                intent.userId(),
                intent.eventId(),
                intent.ticketTypeId(),
                intent.quantity(),
                intent.unitPrice().getAmount(),
                totalAmount,
                intent.reservedAt(),
                intent.expiresAt(),
                null,
                null,
                null,
                null,
                null);
    }

    public static OrderStatusResult fromReservation(ReserveTicketResult result) {
        BigDecimal totalAmount = result.unitPrice().multiply(BigDecimal.valueOf(result.quantity()));
        return new OrderStatusResult(
                result.orderId(),
                result.status(),
                result.userId(),
                result.eventId(),
                result.ticketTypeId(),
                result.quantity(),
                result.unitPrice(),
                totalAmount,
                result.reservedAt(),
                result.expiresAt(),
                null,
                null,
                null,
                null,
                null);
    }

    public static OrderStatusResult fromOrder(Order order, Payment payment) {
        OrderItem item = order.getItems().isEmpty() ? null : order.getItems().get(0);
        return new OrderStatusResult(
                order.getId(),
                OrderTrackingStatus.from(order.getStatus()),
                order.getUserId(),
                order.getEventId(),
                item == null ? null : item.getTicketTypeId(),
                item == null ? null : item.getQuantity(),
                item == null ? null : item.getUnitPrice().getAmount(),
                order.getTotalAmount().getAmount(),
                order.getReservedAt(),
                order.getExpiresAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                payment == null ? null : payment.getProvider(),
                payment == null ? null : payment.getTransactionRef(),
                payment == null ? null : payment.getStatus());
    }
}

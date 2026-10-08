package com.tienphat.application.payment;

import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.PaymentStatus;

import java.util.UUID;

public record PaymentCallbackResult(
        UUID paymentId,
        UUID orderId,
        String transactionRef,
        PaymentStatus paymentStatus,
        OrderStatus orderStatus,
        PaymentCallbackOutcome outcome,
        int ticketCount) {
}

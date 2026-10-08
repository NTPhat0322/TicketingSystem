package com.tienphat.presentation.payment.dto;

import com.tienphat.application.payment.PaymentCallbackOutcome;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.PaymentStatus;

import java.util.UUID;

public record PaymentCallbackResponse(
        UUID paymentId,
        UUID orderId,
        String transactionRef,
        PaymentStatus paymentStatus,
        OrderStatus orderStatus,
        PaymentCallbackOutcome outcome,
        int ticketCount) {
}

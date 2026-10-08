package com.tienphat.application.payment;

import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentInitiationResult(
        UUID paymentId,
        UUID orderId,
        PaymentProvider provider,
        BigDecimal amount,
        String transactionRef,
        PaymentStatus status) {
}

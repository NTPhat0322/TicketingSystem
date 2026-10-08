package com.tienphat.presentation.payment.dto;

import com.tienphat.domain.model.PaymentProvider;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentCallbackRequest(
        @NotBlank String transactionRef,
        @NotNull PaymentProvider provider,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull Boolean success) {
}

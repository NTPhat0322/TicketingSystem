package com.tienphat.presentation.tickettype.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTicketTypeRequest(
        @NotNull UUID eventId,
        @NotBlank String name,
        @NotNull @PositiveOrZero BigDecimal price,
        @Positive int totalQuantity,
        @Positive int maxPerUser,
        @Positive int holdDurationSec) {
}

package com.tienphat.presentation.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Request for one TicketType reservation. The user identity comes from the JWT. */
public record ReserveOrderRequest(
        @NotNull UUID eventId,
        @NotNull UUID ticketTypeId,
        @Positive int quantity) {
}

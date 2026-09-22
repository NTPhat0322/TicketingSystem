package com.tienphat.presentation.tickettype.dto;

import com.tienphat.domain.model.TicketTypeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TicketTypeResponse(
        UUID id,
        UUID eventId,
        String name,
        BigDecimal price,
        int totalQuantity,
        int soldQuantity,
        int maxPerUser,
        int holdDurationSec,
        int version,
        TicketTypeStatus status,
        Instant createdAt,
        Instant updatedAt) {
}

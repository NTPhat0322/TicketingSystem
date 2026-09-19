package com.tienphat.application.tickettype;

import com.tienphat.domain.model.TicketTypeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TicketTypeResult(
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

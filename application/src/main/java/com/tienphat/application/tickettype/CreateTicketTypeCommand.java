package com.tienphat.application.tickettype;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTicketTypeCommand(
        UUID eventId,
        String name,
        BigDecimal price,
        int totalQuantity,
        int maxPerUser,
        int holdDurationSec) {
}

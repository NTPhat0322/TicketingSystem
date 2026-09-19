package com.tienphat.application.tickettype;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateTicketTypeCommand(
        UUID id,
        String name,
        BigDecimal price,
        int totalQuantity,
        int maxPerUser,
        int holdDurationSec) {
}

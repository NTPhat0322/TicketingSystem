package com.tienphat.application.tickettype;

import com.tienphat.application.auth.AuthorizationContext;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateTicketTypeCommand(
        UUID id,
        AuthorizationContext actor,
        String name,
        BigDecimal price,
        int totalQuantity,
        int maxPerUser,
        int holdDurationSec) {
}

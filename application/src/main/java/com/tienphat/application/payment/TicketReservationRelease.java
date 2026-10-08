package com.tienphat.application.payment;

import java.util.UUID;

public record TicketReservationRelease(
        UUID orderId,
        UUID ticketTypeId,
        UUID userId,
        int quantity) {
}

package com.tienphat.presentation.ticket.dto;

import com.tienphat.domain.model.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID ticketId,
        String ticketCode,
        TicketStatus status,
        Instant issuedAt,
        Instant checkedInAt,
        UUID orderItemId,
        UUID ticketTypeId,
        String ticketTypeName,
        UUID eventId,
        String eventName,
        String venueName,
        Instant eventStartTime,
        Instant eventEndTime) {
}

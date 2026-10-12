package com.tienphat.application.ticket;

import com.tienphat.domain.model.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/** A ticket with enough event context to render it; names are null if the type or event is gone. */
public record TicketResult(
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

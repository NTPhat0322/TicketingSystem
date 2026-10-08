package com.tienphat.application.reservation;

import com.tienphat.application.auth.AuthorizationContext;

import java.util.UUID;

/** One-ticket-type reservation request made by the authenticated caller. */
public record ReserveTicketCommand(
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        AuthorizationContext actor) {
}

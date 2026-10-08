package com.tienphat.application.order;

import java.util.UUID;

/** Outcome plus the exact reservation coordinates that may be released after commit. */
public record ExpireOrderResult(
        UUID orderId,
        ExpiryOutcome outcome,
        ReleaseReservation releaseReservation) {

    public boolean requiresRelease() {
        return releaseReservation != null;
    }

    public ExpireOrderResult withRelease(ReleaseReservation release) {
        return new ExpireOrderResult(orderId, outcome, release);
    }

    public record ReleaseReservation(
            UUID orderId,
            UUID ticketTypeId,
            UUID userId,
            int quantity) {
    }
}

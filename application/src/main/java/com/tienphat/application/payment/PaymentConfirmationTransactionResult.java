package com.tienphat.application.payment;

public record PaymentConfirmationTransactionResult(
        PaymentCallbackResult callback,
        TicketReservationRelease reservationRelease) {
}

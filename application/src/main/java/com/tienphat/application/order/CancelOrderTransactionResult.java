package com.tienphat.application.order;

/** The committed cancellation plus the Redis hold coordinates to release after commit. */
public record CancelOrderTransactionResult(
        OrderStatusResult order,
        ExpireOrderResult.ReleaseReservation release) {
}

package com.tienphat.application.payment;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.port.StockCachePort;

/** Completes the database transaction, then reconciles its Redis hold after commit. */
public class ConfirmPaymentUseCase
        implements UseCase<ConfirmPaymentCommand, PaymentCallbackResult> {

    private final PaymentConfirmationTransaction transaction;
    private final ReservationIntentStore reservationIntentStore;
    private final StockCachePort stockCachePort;

    public ConfirmPaymentUseCase(
            PaymentConfirmationTransaction transaction,
            ReservationIntentStore reservationIntentStore,
            StockCachePort stockCachePort) {
        this.transaction = transaction;
        this.reservationIntentStore = reservationIntentStore;
        this.stockCachePort = stockCachePort;
    }

    @Override
    public PaymentCallbackResult execute(ConfirmPaymentCommand command) {
        PaymentConfirmationTransactionResult transactionResult = transaction.execute(command);
        PaymentCallbackResult result = transactionResult.callback();

        if (transactionResult.reservationRelease() == null) {
            reservationIntentStore.markCompleted(result.orderId());
        } else {
            TicketReservationRelease release = transactionResult.reservationRelease();
            reservationIntentStore.markExpired(release.orderId());
            stockCachePort.release(
                    release.orderId(), release.ticketTypeId(), release.userId(), release.quantity());
        }
        return result;
    }
}

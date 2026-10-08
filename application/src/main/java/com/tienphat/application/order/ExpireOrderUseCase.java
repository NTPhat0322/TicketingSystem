package com.tienphat.application.order;

import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.port.StockCachePort;

import java.time.Clock;
import java.time.Instant;

/** Coordinates the committed Order expiry transition with idempotent Redis release. */
public class ExpireOrderUseCase {

    private final ExpireOrderTransaction transaction;
    private final ReservationIntentStore intentStore;
    private final StockCachePort stockCachePort;
    private final Clock clock;

    public ExpireOrderUseCase(
            ExpireOrderTransaction transaction,
            ReservationIntentStore intentStore,
            StockCachePort stockCachePort) {
        this(transaction, intentStore, stockCachePort, Clock.systemUTC());
    }

    public ExpireOrderUseCase(
            ExpireOrderTransaction transaction,
            ReservationIntentStore intentStore,
            StockCachePort stockCachePort,
            Clock clock) {
        this.transaction = transaction;
        this.intentStore = intentStore;
        this.stockCachePort = stockCachePort;
        this.clock = clock;
    }

    public ExpireOrderResult execute(ExpireOrderCommand command) {
        ExpireOrderResult result = transaction.execute(command);

        if (result.outcome() == ExpiryOutcome.ORDER_MISSING) {
            return releaseMissingOrder(command);
        }
        if (result.requiresRelease()) {
            releaseAfterDatabaseCommit(result.releaseReservation());
        } else if (result.outcome() == ExpiryOutcome.PAID) {
            // A payment won the race. Keep the sale-time stock untouched and stop expiry scans.
            intentStore.markCompleted(command.orderId());
        }
        return result;
    }

    private ExpireOrderResult releaseMissingOrder(ExpireOrderCommand command) {
        ReservationIntent intent = intentStore.findByOrderId(command.orderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Reservation intent is missing for expired order " + command.orderId()));
        if (intent.expiresAt().isAfter(clock.instant())) {
            return new ExpireOrderResult(command.orderId(), ExpiryOutcome.NOT_DUE, null);
        }

        intentStore.markExpired(command.orderId());
        ExpireOrderResult.ReleaseReservation release = new ExpireOrderResult.ReleaseReservation(
                intent.orderId(), intent.ticketTypeId(), intent.userId(), intent.quantity());
        stockCachePort.release(
                release.orderId(), release.ticketTypeId(), release.userId(), release.quantity());
        return new ExpireOrderResult(command.orderId(), ExpiryOutcome.ORDER_MISSING, release);
    }

    private void releaseAfterDatabaseCommit(ExpireOrderResult.ReleaseReservation release) {
        intentStore.markExpired(release.orderId());
        stockCachePort.release(
                release.orderId(), release.ticketTypeId(), release.userId(), release.quantity());
    }
}

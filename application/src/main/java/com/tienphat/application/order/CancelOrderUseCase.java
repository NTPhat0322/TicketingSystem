package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.InvalidOrderStateException;
import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.exception.OrderNotFoundException;
import com.tienphat.domain.exception.ReservationServiceUnavailableException;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.ReservationIntentStatusPort;
import com.tienphat.domain.port.StockCachePort;

/**
 * Cancels a PENDING_PAYMENT order, then restores its Redis hold after commit. A failed release leaves the
 * order CANCELLED; a retry or the expiry message releases it later because the release is idempotent.
 */
public class CancelOrderUseCase implements UseCase<CancelOrderCommand, OrderStatusResult> {

    private final CancelOrderTransaction transaction;
    private final ReservationIntentStatusPort reservationIntentStatusPort;
    private final StockCachePort stockCachePort;

    public CancelOrderUseCase(
            CancelOrderTransaction transaction,
            ReservationIntentStatusPort reservationIntentStatusPort,
            StockCachePort stockCachePort) {
        this.transaction = transaction;
        this.reservationIntentStatusPort = reservationIntentStatusPort;
        this.stockCachePort = stockCachePort;
    }

    @Override
    public OrderStatusResult execute(CancelOrderCommand command) {
        validateCommand(command);
        command.actor().requireAuthenticated();

        CancelOrderTransactionResult result = transaction.execute(command)
                .orElseThrow(() -> rejectWithoutOrder(command));

        ExpireOrderResult.ReleaseReservation release = result.release();
        stockCachePort.release(
                release.orderId(), release.ticketTypeId(), release.userId(), release.quantity());
        return result.order();
    }

    private RuntimeException rejectWithoutOrder(CancelOrderCommand command) {
        ReservationIntent intent;
        try {
            intent = reservationIntentStatusPort.findByOrderId(command.orderId())
                    .orElseThrow(() -> new OrderNotFoundException(
                            "Order " + command.orderId() + " not found"));
        } catch (OrderNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ReservationServiceUnavailableException(
                    "Reservation status service is unavailable", exception);
        }
        AuthorizationContext actor = command.actor();
        actor.requireCanAccess(intent.userId());

        boolean expired = intent.state() == ReservationIntentState.EXPIRED
                || intent.state() == ReservationIntentState.RELEASED;
        return new InvalidOrderStateException(expired
                ? "Order " + command.orderId() + " has already expired"
                : "Order " + command.orderId() + " is still being created; retry shortly");
    }

    private static void validateCommand(CancelOrderCommand command) {
        if (command == null) {
            throw new InvalidReservationRequestException("Cancel order command must not be null");
        }
        if (command.orderId() == null) {
            throw new InvalidReservationRequestException("Cancel order orderId must not be null");
        }
        if (command.actor() == null) {
            throw new InvalidReservationRequestException("Cancel order actor must not be null");
        }
    }
}

package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.exception.InvalidOrderStateException;
import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.exception.OrderNotFoundException;
import com.tienphat.domain.exception.ReservationServiceUnavailableException;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.ReservationIntentStatusPort;
import com.tienphat.domain.port.StockCachePort;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CancelOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    private static final UUID ORDER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final AuthorizationContext OWNER = new AuthorizationContext(USER_ID, UserRole.CUSTOMER);
    private static final CancelOrderCommand COMMAND = new CancelOrderCommand(ORDER_ID, OWNER);

    private final CancelOrderTransaction transaction = mock(CancelOrderTransaction.class);
    private final ReservationIntentStatusPort intentStatusPort = mock(ReservationIntentStatusPort.class);
    private final StockCachePort stockCachePort = mock(StockCachePort.class);
    private final CancelOrderUseCase useCase = new CancelOrderUseCase(transaction, intentStatusPort, stockCachePort);

    @Test
    void releasesHoldOnceAfterCommittedCancellation() {
        OrderStatusResult cancelled = cancelledOrder();
        when(transaction.execute(COMMAND)).thenReturn(Optional.of(new CancelOrderTransactionResult(
                cancelled, new ExpireOrderResult.ReleaseReservation(ORDER_ID, TICKET_TYPE_ID, USER_ID, 2))));

        assertThat(useCase.execute(COMMAND)).isSameAs(cancelled);

        var order = inOrder(transaction, stockCachePort);
        order.verify(transaction).execute(COMMAND);
        order.verify(stockCachePort).release(ORDER_ID, TICKET_TYPE_ID, USER_ID, 2);
        order.verifyNoMoreInteractions();
        verifyNoInteractions(intentStatusPort);
    }

    @Test
    void releaseFailurePropagatesAfterCommit() {
        when(transaction.execute(COMMAND)).thenReturn(Optional.of(new CancelOrderTransactionResult(
                cancelledOrder(), new ExpireOrderResult.ReleaseReservation(ORDER_ID, TICKET_TYPE_ID, USER_ID, 2))));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(stockCachePort).release(ORDER_ID, TICKET_TYPE_ID, USER_ID, 2);

        assertThatThrownBy(() -> useCase.execute(COMMAND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");
    }

    @Test
    void unknownOrderWithoutIntentIsNotFound() {
        when(transaction.execute(COMMAND)).thenReturn(Optional.empty());
        when(intentStatusPort.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(COMMAND)).isInstanceOf(OrderNotFoundException.class);
        verifyNoInteractions(stockCachePort);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationIntentState.class, names = {"PENDING", "ENQUEUED"})
    void orderStillBeingCreatedIsConflict(ReservationIntentState state) {
        when(transaction.execute(COMMAND)).thenReturn(Optional.empty());
        when(intentStatusPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(intent(USER_ID, state)));

        assertThatThrownBy(() -> useCase.execute(COMMAND))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("still being created");
        verifyNoInteractions(stockCachePort);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationIntentState.class, names = {"EXPIRED", "RELEASED"})
    void expiredIntentIsConflict(ReservationIntentState state) {
        when(transaction.execute(COMMAND)).thenReturn(Optional.empty());
        when(intentStatusPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(intent(USER_ID, state)));

        assertThatThrownBy(() -> useCase.execute(COMMAND))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("already expired");
        verifyNoInteractions(stockCachePort);
    }

    @Test
    void foreignIntentIsForbidden() {
        when(transaction.execute(COMMAND)).thenReturn(Optional.empty());
        when(intentStatusPort.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(intent(UUID.randomUUID(), ReservationIntentState.ENQUEUED)));

        assertThatThrownBy(() -> useCase.execute(COMMAND)).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(stockCachePort);
    }

    @Test
    void intentLookupFailureIsServiceUnavailable() {
        when(transaction.execute(COMMAND)).thenReturn(Optional.empty());
        when(intentStatusPort.findByOrderId(ORDER_ID)).thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> useCase.execute(COMMAND))
                .isInstanceOf(ReservationServiceUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        verifyNoInteractions(stockCachePort);
    }

    @Test
    void invalidCommandIsRejectedBeforeTransaction() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> useCase.execute(new CancelOrderCommand(null, OWNER)))
                .isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> useCase.execute(new CancelOrderCommand(ORDER_ID, null)))
                .isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> useCase.execute(
                new CancelOrderCommand(ORDER_ID, new AuthorizationContext(null, null))))
                .isInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(transaction, intentStatusPort, stockCachePort);
    }

    private static OrderStatusResult cancelledOrder() {
        return new OrderStatusResult(
                ORDER_ID, OrderTrackingStatus.CANCELLED, USER_ID, EVENT_ID, TICKET_TYPE_ID,
                2, new BigDecimal("150000.00"), new BigDecimal("300000.00"),
                NOW, NOW.plusSeconds(300), NOW, NOW, null, null, null);
    }

    private static ReservationIntent intent(UUID userId, ReservationIntentState state) {
        return new ReservationIntent(ORDER_ID, userId, EVENT_ID, TICKET_TYPE_ID, 2,
                Money.of(new BigDecimal("150000")), 4, 300, NOW, NOW.plusSeconds(300), state, 0, NOW);
    }
}

package com.tienphat.application.order;

import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.port.StockCachePort;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExpireOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:10:00Z");
    private static final UUID ORDER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final Money UNIT_PRICE = Money.of(new BigDecimal("150000"));
    private static final Instant RESERVED_AT = NOW.minusSeconds(300);
    private static final Instant EXPIRES_AT = NOW.minusSeconds(1);

    private final ExpireOrderTransaction transaction = mock(ExpireOrderTransaction.class);
    private final ReservationIntentStore intentStore = mock(ReservationIntentStore.class);
    private final StockCachePort stockCachePort = mock(StockCachePort.class);
    private final ExpireOrderUseCase useCase = new ExpireOrderUseCase(
            transaction,
            intentStore,
            stockCachePort,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void releasesAfterCommittedPendingPaymentExpiry() {
        ExpireOrderResult.ReleaseReservation release = release();
        ExpireOrderResult transition = new ExpireOrderResult(ORDER_ID, ExpiryOutcome.EXPIRED, release);
        when(transaction.execute(any())).thenReturn(transition);

        assertThat(useCase.execute(command())).isEqualTo(transition);

        var order = inOrder(transaction, intentStore, stockCachePort);
        order.verify(transaction).execute(any());
        order.verify(intentStore).markExpired(ORDER_ID);
        order.verify(stockCachePort).release(ORDER_ID, TICKET_TYPE_ID, USER_ID, 2);
    }

    @Test
    void paidRaceNeverReleasesAndCompletesIntent() {
        ExpireOrderResult transition = new ExpireOrderResult(ORDER_ID, ExpiryOutcome.PAID, null);
        when(transaction.execute(any())).thenReturn(transition);

        assertThat(useCase.execute(command()).outcome()).isEqualTo(ExpiryOutcome.PAID);

        verify(intentStore).markCompleted(ORDER_ID);
        verifyNoInteractions(stockCachePort);
    }

    @Test
    void missingOrderUsesTheRetainedIntentForRelease() {
        ReservationIntent intent = new ReservationIntent(
                ORDER_ID, USER_ID, EVENT_ID, TICKET_TYPE_ID, 2, UNIT_PRICE, 4, 300,
                RESERVED_AT, EXPIRES_AT, ReservationIntentState.ENQUEUED, 1, RESERVED_AT);
        when(transaction.execute(any()))
                .thenReturn(new ExpireOrderResult(ORDER_ID, ExpiryOutcome.ORDER_MISSING, null));
        when(intentStore.findByOrderId(ORDER_ID)).thenReturn(Optional.of(intent));

        ExpireOrderResult result = useCase.execute(command());

        assertThat(result.outcome()).isEqualTo(ExpiryOutcome.ORDER_MISSING);
        verify(intentStore).markExpired(ORDER_ID);
        verify(stockCachePort).release(ORDER_ID, TICKET_TYPE_ID, USER_ID, 2);
    }

    @Test
    void releaseFailureIsPropagatedForReconciliation() {
        when(transaction.execute(any()))
                .thenReturn(new ExpireOrderResult(ORDER_ID, ExpiryOutcome.EXPIRED, release()));
        org.mockito.Mockito.doThrow(new IllegalStateException("redis unavailable"))
                .when(stockCachePort).release(ORDER_ID, TICKET_TYPE_ID, USER_ID, 2);

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");
        verify(intentStore).markExpired(ORDER_ID);
    }

    private static ExpireOrderCommand command() {
        return new ExpireOrderCommand(
                UUID.randomUUID(), ORDER_ID, USER_ID, EVENT_ID, TICKET_TYPE_ID,
                2, UNIT_PRICE, RESERVED_AT, EXPIRES_AT);
    }

    private static ExpireOrderResult.ReleaseReservation release() {
        return new ExpireOrderResult.ReleaseReservation(ORDER_ID, TICKET_TYPE_ID, USER_ID, 2);
    }
}

package com.tienphat.infrastructure.reconciliation;

import com.tienphat.application.order.ExpireOrderUseCase;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.infrastructure.config.MessagingProperties;
import com.tienphat.infrastructure.messaging.ReservationIntentRelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReservationReconciliationJobTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:10:00Z");
    private static final UUID ORDER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();

    private final ReservationIntentStore intentStore = mock(ReservationIntentStore.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ExpireOrderUseCase expireOrderUseCase = mock(ExpireOrderUseCase.class);
    private final ReservationIntentRelay relay = mock(ReservationIntentRelay.class);
    private final MessagingProperties properties = new MessagingProperties();
    private ReservationReconciliationJob job;

    @BeforeEach
    void setUp() {
        properties.setOrderCreationGracePeriod(Duration.ofSeconds(10));
        properties.setReconciliationBatchSize(10);
        properties.setRelayClaimLease(Duration.ofSeconds(15));
        when(relay.relayOnce()).thenReturn(0);
        when(intentStore.tryClaim(any(), any(), any())).thenReturn(true);
        job = new ReservationReconciliationJob(
                intentStore, orderRepository, expireOrderUseCase, relay,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void staleEnqueuedIntentWithoutOrderIsPutBackIntoRelayIndexBeforeExpiry() {
        ReservationIntent intent = intent(
                ReservationIntentState.ENQUEUED,
                NOW.minusSeconds(20),
                NOW.plusSeconds(100));
        when(intentStore.findStaleEnqueued(NOW, Duration.ofSeconds(10), 10))
                .thenReturn(List.of(intent));
        when(intentStore.findExpired(NOW, 10)).thenReturn(List.of());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThat(job.reconcileOnce()).isEqualTo(1);

        verify(intentStore).reschedule(ORDER_ID, 1, NOW);
        verify(intentStore).releaseClaim(eq(ORDER_ID), startsWith("reconcile-"));
        verifyNoInteractions(expireOrderUseCase);
    }

    @Test
    void expiredIntentWithoutOrderDelegatesToIdempotentExpiryUseCase() {
        ReservationIntent intent = intent(
                ReservationIntentState.ENQUEUED,
                NOW.minusSeconds(20),
                NOW.minusSeconds(1));
        when(intentStore.findStaleEnqueued(NOW, Duration.ofSeconds(10), 10))
                .thenReturn(List.of());
        when(intentStore.findExpired(NOW, 10)).thenReturn(List.of(intent));

        assertThat(job.reconcileOnce()).isEqualTo(1);

        verify(expireOrderUseCase).execute(any());
        verify(intentStore).releaseClaim(eq(ORDER_ID), startsWith("reconcile-"));
    }

    private static ReservationIntent intent(
            ReservationIntentState state,
            Instant enqueuedAt,
            Instant expiresAt) {
        return new ReservationIntent(
                ORDER_ID, USER_ID, EVENT_ID, TICKET_TYPE_ID, 2,
                com.tienphat.domain.vo.Money.of(new BigDecimal("150000")),
                4, 300, NOW.minusSeconds(30), expiresAt, state, 0, enqueuedAt, enqueuedAt);
    }
}

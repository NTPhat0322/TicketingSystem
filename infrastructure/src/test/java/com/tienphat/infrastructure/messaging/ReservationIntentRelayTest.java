package com.tienphat.infrastructure.messaging;

import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.vo.Money;
import com.tienphat.infrastructure.config.MessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationIntentRelayTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");
    private static final UUID ORDER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");

    private final ReservationIntentStore intentStore = mock(ReservationIntentStore.class);
    private final OrderCreatePublisher publisher = mock(OrderCreatePublisher.class);
    private final OrderExpiryPublisher expiryPublisher = mock(OrderExpiryPublisher.class);
    private final MessagingProperties properties = new MessagingProperties();
    private ReservationIntentRelay relay;

    @BeforeEach
    void setUp() {
        properties.setRelayBatchSize(10);
        properties.setRelayClaimLease(Duration.ofSeconds(15));
        properties.setReservationIntentRetryDelay(Duration.ofSeconds(5));
        properties.setReservationIntentMaxRetryDelay(Duration.ofSeconds(20));
        relay = new ReservationIntentRelay(
                intentStore,
                publisher,
                expiryPublisher,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "test-relay");
        when(intentStore.tryClaim(ORDER_ID, "test-relay", Duration.ofSeconds(15))).thenReturn(true);
    }

    @Test
    void confirmedPublishMarksIntentEnqueuedAndReleasesClaim() {
        ReservationIntent intent = intent(0);
        when(intentStore.findDue(NOW, 10)).thenReturn(List.of(intent));

        assertThat(relay.relayOnce()).isEqualTo(1);

        verify(publisher).publish(intent);
        verify(expiryPublisher).publish(intent);
        verify(intentStore).markEnqueued(ORDER_ID);
        verify(intentStore).releaseClaim(ORDER_ID, "test-relay");
    }

    @Test
    void publishFailureKeepsIntentDiscoverableWithBoundedBackoff() {
        ReservationIntent intent = intent(3);
        when(intentStore.findDue(NOW, 10)).thenReturn(List.of(intent));
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(intent);

        assertThat(relay.relayOnce()).isZero();

        verify(intentStore).reschedule(ORDER_ID, 4, NOW.plusSeconds(20));
        verify(intentStore).releaseClaim(ORDER_ID, "test-relay");
    }

    private static ReservationIntent intent(int retryCount) {
        return new ReservationIntent(
                ORDER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                Money.of(new BigDecimal("150000")),
                4,
                300,
                NOW,
                NOW.plusSeconds(300),
                ReservationIntentState.PENDING,
                retryCount,
                NOW);
    }
}

package com.tienphat.infrastructure.messaging;

import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.infrastructure.config.MessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Relays due Redis intents to RabbitMQ without deleting them before confirmation. */
@Component
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = {"enabled", "relay-enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class ReservationIntentRelay {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationIntentRelay.class);

    private final ReservationIntentStore intentStore;
    private final OrderCreatePublisher publisher;
    private final OrderExpiryPublisher expiryPublisher;
    private final MessagingProperties properties;
    private final Clock clock;
    private final String owner;

    @Autowired
    public ReservationIntentRelay(
            ReservationIntentStore intentStore,
            OrderCreatePublisher publisher,
            OrderExpiryPublisher expiryPublisher,
            MessagingProperties properties) {
        this(intentStore, publisher, expiryPublisher, properties, Clock.systemUTC(), "relay-" + UUID.randomUUID());
    }

    ReservationIntentRelay(
            ReservationIntentStore intentStore,
            OrderCreatePublisher publisher,
            OrderExpiryPublisher expiryPublisher,
            MessagingProperties properties,
            Clock clock,
            String owner) {
        this.intentStore = intentStore;
        this.publisher = publisher;
        this.expiryPublisher = expiryPublisher;
        this.properties = properties;
        this.clock = clock;
        this.owner = owner;
    }

    @Scheduled(
            fixedDelayString = "${ticketing.messaging.relay-interval:1s}",
            initialDelayString = "${ticketing.messaging.relay-interval:1s}")
    public void scheduledRelay() {
        relayOnce();
    }

    public int relayOnce() {
        Instant now = clock.instant();
        List<ReservationIntent> due;
        try {
            due = intentStore.findDue(now, properties.getRelayBatchSize());
        } catch (RuntimeException exception) {
            LOG.warn("Reservation relay could not read due intents", exception);
            return 0;
        }

        int published = 0;
        for (ReservationIntent intent : due) {
            if (!intentStore.tryClaim(intent.orderId(), owner, properties.getRelayClaimLease())) {
                continue;
            }

            try {
                publisher.publish(intent);
                expiryPublisher.publish(intent);
                intentStore.markEnqueued(intent.orderId());
                published++;
            } catch (RuntimeException exception) {
                reschedule(intent, exception);
            } finally {
                try {
                    intentStore.releaseClaim(intent.orderId(), owner);
                } catch (RuntimeException exception) {
                    LOG.warn("Reservation relay could not release claim for order {}",
                            intent.orderId(), exception);
                }
            }
        }
        return published;
    }

    private void reschedule(ReservationIntent intent, RuntimeException exception) {
        int retryCount = intent.retryCount() + 1;
        Instant nextRetryAt = clock.instant().plus(backoff(retryCount));
        try {
            intentStore.reschedule(intent.orderId(), retryCount, nextRetryAt);
            LOG.warn("Reservation relay publish failed for order {}; retry {} at {}",
                    intent.orderId(), retryCount, nextRetryAt, exception);
        } catch (RuntimeException rescheduleException) {
            LOG.error("Reservation relay lost retry update for order {}",
                    intent.orderId(), rescheduleException);
        }
    }

    private Duration backoff(int retryCount) {
        Duration base = properties.getReservationIntentRetryDelay();
        Duration max = properties.getReservationIntentMaxRetryDelay();
        Duration delay = base;
        for (int attempt = 1; attempt < retryCount && delay.compareTo(max) < 0; attempt++) {
            Duration doubled = delay.multipliedBy(2);
            delay = doubled.compareTo(max) > 0 ? max : doubled;
        }
        return delay.compareTo(max) > 0 ? max : delay;
    }
}

package com.tienphat.infrastructure.reconciliation;

import com.tienphat.application.order.ExpireOrderCommand;
import com.tienphat.application.order.ExpireOrderUseCase;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.infrastructure.config.MessagingProperties;
import com.tienphat.infrastructure.messaging.ReservationIntentRelay;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Repairs cross-store gaps left by a process crash between Redis, RabbitMQ, and PostgreSQL. */
@Component
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = {"enabled", "consumers-enabled"},
        havingValue = "true")
public class ReservationReconciliationJob {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationReconciliationJob.class);

    private final ReservationIntentStore intentStore;
    private final OrderRepository orderRepository;
    private final ExpireOrderUseCase expireOrderUseCase;
    private final ReservationIntentRelay relay;
    private final MessagingProperties properties;
    private final Clock clock;
    private final String owner = "reconcile-" + UUID.randomUUID();

    @Autowired
    public ReservationReconciliationJob(
            ReservationIntentStore intentStore,
            OrderRepository orderRepository,
            ExpireOrderUseCase expireOrderUseCase,
            ReservationIntentRelay relay,
            MessagingProperties properties) {
        this(intentStore, orderRepository, expireOrderUseCase, relay, properties, Clock.systemUTC());
    }

    ReservationReconciliationJob(
            ReservationIntentStore intentStore,
            OrderRepository orderRepository,
            ExpireOrderUseCase expireOrderUseCase,
            ReservationIntentRelay relay,
            MessagingProperties properties,
            Clock clock) {
        this.intentStore = intentStore;
        this.orderRepository = orderRepository;
        this.expireOrderUseCase = expireOrderUseCase;
        this.relay = relay;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${ticketing.messaging.reconciliation-interval:1m}",
            initialDelayString = "${ticketing.messaging.reconciliation-interval:1m}")
    public void scheduledReconcile() {
        reconcileOnce();
    }

    public int reconcileOnce() {
        Instant now = clock.instant();
        int repaired = relay.relayOnce();
        Set<UUID> handled = new HashSet<>();

        repaired += reconcileStaleEnqueued(now, handled);
        repaired += reconcileExpired(now, handled);
        return repaired;
    }

    private int reconcileStaleEnqueued(Instant now, Set<UUID> handled) {
        List<ReservationIntent> intents = intentStore.findStaleEnqueued(
                now, properties.getOrderCreationGracePeriod(), properties.getReconciliationBatchSize());
        int repaired = 0;
        for (ReservationIntent intent : intents) {
            if (!claim(intent)) {
                continue;
            }
            try {
                handled.add(intent.orderId());
                Order order = orderRepository.findById(intent.orderId()).orElse(null);
                if (order == null) {
                    if (intent.expiresAt().isAfter(now)) {
                        intentStore.reschedule(
                                intent.orderId(), intent.retryCount() + 1, now);
                        repaired++;
                    } else {
                        expire(intent);
                        repaired++;
                    }
                } else if (order.getStatus() == OrderStatus.PAID) {
                    intentStore.markCompleted(intent.orderId());
                    repaired++;
                } else if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                        && !order.getExpiresAt().isAfter(now)) {
                    expire(intent);
                    repaired++;
                } else if (order.getStatus() == OrderStatus.EXPIRED) {
                    expire(intent);
                    repaired++;
                } else {
                    intentStore.markOrderCreated(intent.orderId());
                    repaired++;
                }
            } catch (RuntimeException exception) {
                LOG.warn("Reservation reconciliation failed orderId={} ticketTypeId={} attempt={} state={}",
                        intent.orderId(), intent.ticketTypeId(), intent.retryCount(), intent.state(), exception);
            } finally {
                releaseClaim(intent);
            }
        }
        return repaired;
    }

    private int reconcileExpired(Instant now, Set<UUID> handled) {
        List<ReservationIntent> intents = intentStore.findExpired(
                now, properties.getReconciliationBatchSize());
        int repaired = 0;
        for (ReservationIntent intent : intents) {
            if (handled.contains(intent.orderId()) || !claim(intent)) {
                continue;
            }
            try {
                handled.add(intent.orderId());
                expire(intent);
                repaired++;
            } catch (RuntimeException exception) {
                LOG.warn("Expired reservation reconciliation failed orderId={} ticketTypeId={} attempt={} state={}",
                        intent.orderId(), intent.ticketTypeId(), intent.retryCount(), intent.state(), exception);
            } finally {
                releaseClaim(intent);
            }
        }
        return repaired;
    }

    private void expire(ReservationIntent intent) {
        expireOrderUseCase.execute(new ExpireOrderCommand(
                UUID.randomUUID(),
                intent.orderId(),
                intent.userId(),
                intent.eventId(),
                intent.ticketTypeId(),
                intent.quantity(),
                intent.unitPrice(),
                intent.reservedAt(),
                intent.expiresAt()));
    }

    private boolean claim(ReservationIntent intent) {
        return intentStore.tryClaim(intent.orderId(), owner, properties.getRelayClaimLease());
    }

    private void releaseClaim(ReservationIntent intent) {
        try {
            intentStore.releaseClaim(intent.orderId(), owner);
        } catch (RuntimeException exception) {
            LOG.warn("Reservation reconciliation could not release claim orderId={} ticketTypeId={}",
                    intent.orderId(), intent.ticketTypeId(), exception);
        }
    }
}

package com.tienphat.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically drains committed outbox rows. */
@Component
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = {"enabled", "outbox-publisher-enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRelayTransaction relay;

    public OutboxPublisher(OutboxRelayTransaction relay) {
        this.relay = relay;
    }

    @Scheduled(
            fixedDelayString = "${ticketing.messaging.outbox-publisher-interval:1s}",
            initialDelayString = "${ticketing.messaging.outbox-publisher-interval:1s}")
    public void scheduledPublish() {
        try {
            OutboxRelayTransaction.OutboxPublishResult result = relay.publishPending();
            if (result.published() > 0 || result.retried() > 0) {
                LOG.info("Outbox publisher processed published={} retried={}",
                        result.published(), result.retried());
            }
        } catch (RuntimeException exception) {
            LOG.warn("Outbox publisher could not drain pending events", exception);
        }
    }

    public OutboxRelayTransaction.OutboxPublishResult publishOnce() {
        return relay.publishPending();
    }
}

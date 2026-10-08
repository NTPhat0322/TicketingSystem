package com.tienphat.infrastructure.outbox;

import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.repository.OutboxEventRepository;
import com.tienphat.infrastructure.config.MessagingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Holds SKIP LOCKED rows until their broker attempt and state update finish. */
@Component
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = {"enabled", "outbox-publisher-enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class OutboxRelayTransaction {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventMessagePublisher publisher;
    private final MessagingProperties properties;

    public OutboxRelayTransaction(
            OutboxEventRepository outboxEventRepository,
            OutboxEventMessagePublisher publisher,
            MessagingProperties properties) {
        this.outboxEventRepository = outboxEventRepository;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Transactional
    public OutboxPublishResult publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findAllPending();
        int batchSize = Math.max(1, properties.getOutboxBatchSize());
        int published = 0;
        int retried = 0;
        int limit = Math.min(batchSize, pending.size());

        for (int index = 0; index < limit; index++) {
            OutboxEvent event = pending.get(index);
            try {
                publisher.publish(event);
                event.markPublished();
                outboxEventRepository.save(event);
                published++;
            } catch (RuntimeException exception) {
                // Exercise FAILED -> PENDING explicitly. The final committed state stays retryable,
                // while a future schema can persist an attempt counter without changing this loop.
                try {
                    event.markFailed();
                    event.retry();
                    outboxEventRepository.save(event);
                    retried++;
                } catch (RuntimeException stateException) {
                    throw new IllegalStateException(
                            "Unable to return outbox event " + event.getId() + " to PENDING", stateException);
                }
            }
        }
        return new OutboxPublishResult(published, retried);
    }

    public record OutboxPublishResult(int published, int retried) {
    }
}

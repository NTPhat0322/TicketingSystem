package com.tienphat.infrastructure.outbox;

import com.tienphat.domain.model.AggregateType;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.model.OutboxEventType;
import com.tienphat.domain.model.OutboxEventStatus;
import com.tienphat.domain.repository.OutboxEventRepository;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import com.tienphat.infrastructure.config.MessagingProperties;
import com.tienphat.infrastructure.messaging.AbstractRedisRabbitIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfrastructureTestApplication.class)
@TestPropertySource(properties = {
        "ticketing.messaging.consumers-enabled=false",
        "ticketing.messaging.outbox-publisher-enabled=true",
        "ticketing.messaging.outbox-publisher-interval=1h"
})
class OutboxRabbitIntegrationTest extends AbstractRedisRabbitIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private OutboxEventRepository outboxEventRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private OutboxPublisher outboxPublisher;

    @org.springframework.beans.factory.annotation.Autowired
    private RabbitTemplate rabbitTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private MessagingProperties properties;

    @Test
    void pendingPaymentSuccessIsPublishedAndMarkedPublished() {
        UUID paymentId = UUID.randomUUID();
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.record(
                AggregateType.PAYMENT,
                paymentId,
                OutboxEventType.PAYMENT_SUCCESS,
                "{\"schemaVersion\":1,\"orderId\":\"" + UUID.randomUUID() + "\"}"));

        OutboxRelayTransaction.OutboxPublishResult result = outboxPublisher.publishOnce();

        // The shared PostgreSQL container may contain pending rows created by an earlier
        // integration test. The relay is intentionally allowed to drain all of them in one
        // batch, so this test asserts that its own event was published instead of assuming a
        // pristine database.
        assertThat(result.published()).isGreaterThanOrEqualTo(1);
        assertThat(result.retried()).isZero();
        assertThat(outboxEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);

        Message message = receiveMessageForEvent(event.getId().toString());
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.getId().toString());
        assertThat(new String(message.getBody())).contains("schemaVersion");
    }

    private Message receiveMessageForEvent(String messageId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Message candidate = rabbitTemplate.receive(properties.getOutboxPaymentSuccessQueue(), 250);
            if (candidate == null) {
                continue;
            }
            if (messageId.equals(candidate.getMessageProperties().getMessageId())) {
                return candidate;
            }
        }
        return null;
    }
}

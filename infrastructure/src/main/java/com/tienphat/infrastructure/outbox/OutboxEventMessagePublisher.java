package com.tienphat.infrastructure.outbox;

import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.model.OutboxEventType;
import com.tienphat.infrastructure.config.MessagingProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Publishes a persisted outbox row as a durable RabbitMQ message with publisher confirmation. */
@Component
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = {"enabled", "outbox-publisher-enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class OutboxEventMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    public OutboxEventMessagePublisher(
            RabbitTemplate rabbitTemplate,
            MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(OutboxEvent event) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setMessageId(event.getId().toString());
        messageProperties.setHeader("eventType", event.getEventType().name());
        messageProperties.setHeader("aggregateType", event.getAggregateType().name());
        messageProperties.setHeader("aggregateId", event.getAggregateId().toString());

        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), messageProperties);
        rabbitTemplate.invoke(operations -> {
            operations.send(
                    properties.getOutboxExchange(),
                    routingKey(event.getEventType()),
                    message);
            operations.waitForConfirmsOrDie(properties.getPublisherConfirmTimeout().toMillis());
            return null;
        });
    }

    private String routingKey(OutboxEventType eventType) {
        return switch (eventType) {
            case ORDER_CREATED -> properties.getOutboxOrderCreatedRoutingKey();
            case ORDER_EXPIRED -> properties.getOutboxOrderExpiredRoutingKey();
            case PAYMENT_SUCCESS -> properties.getOutboxPaymentSuccessRoutingKey();
        };
    }
}

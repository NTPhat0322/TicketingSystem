package com.tienphat.infrastructure.messaging;

import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.infrastructure.config.MessagingProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/** Publishes a durable per-reservation TTL message whose DLX route signals expiry. */
@Component
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrderExpiryPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;
    private final OrderExpiryMessageCodec codec;
    private final Clock clock;

    @Autowired
    public OrderExpiryPublisher(
            RabbitTemplate rabbitTemplate,
            MessagingProperties properties,
            OrderExpiryMessageCodec codec) {
        this(rabbitTemplate, properties, codec, Clock.systemUTC());
    }

    OrderExpiryPublisher(
            RabbitTemplate rabbitTemplate,
            MessagingProperties properties,
            OrderExpiryMessageCodec codec,
            Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.codec = codec;
        this.clock = clock;
    }

    public OrderExpiryMessage publish(ReservationIntent intent) {
        OrderExpiryMessage message = OrderExpiryMessage.fromIntent(intent);
        long delayMillis = Math.max(1L, Duration.between(clock.instant(), intent.expiresAt()).toMillis());

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setContentEncoding("UTF-8");
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setExpiration(Long.toString(delayMillis));
        messageProperties.setMessageId(message.messageId().toString());
        messageProperties.setHeader("schemaVersion", message.schemaVersion());
        messageProperties.setHeader("orderId", message.orderId().toString());
        messageProperties.setHeader("expiresAt", message.expiresAt());

        Message amqpMessage = new Message(codec.encode(message), messageProperties);
        rabbitTemplate.invoke(operations -> {
            operations.send(
                    properties.getOrderExchange(),
                    properties.getOrderHoldDelayRoutingKey(),
                    amqpMessage);
            operations.waitForConfirmsOrDie(properties.getPublisherConfirmTimeout().toMillis());
            return null;
        });
        return message;
    }
}

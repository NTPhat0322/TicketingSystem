package com.tienphat.infrastructure.messaging;

import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.infrastructure.config.MessagingProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publishes order-create messages and waits for a broker publisher confirmation. */
@Component
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrderCreatePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;
    private final OrderCreateMessageCodec codec;

    public OrderCreatePublisher(
            RabbitTemplate rabbitTemplate,
            MessagingProperties properties,
            OrderCreateMessageCodec codec) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.codec = codec;
    }

    public OrderCreateMessage publish(ReservationIntent intent) {
        OrderCreateMessage message = OrderCreateMessage.fromIntent(intent);
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setContentEncoding("UTF-8");
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setMessageId(message.messageId().toString());
        messageProperties.setHeader("schemaVersion", message.schemaVersion());
        messageProperties.setHeader("orderId", message.orderId().toString());

        Message amqpMessage = new Message(codec.encode(message), messageProperties);
        rabbitTemplate.invoke(operations -> {
            operations.send(
                    properties.getOrderExchange(),
                    properties.getOrderCreateRoutingKey(),
                    amqpMessage);
            operations.waitForConfirmsOrDie(properties.getPublisherConfirmTimeout().toMillis());
            return null;
        });
        return message;
    }
}

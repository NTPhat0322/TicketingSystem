package com.tienphat.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RabbitTopologyConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_CREATE_QUEUE = "order.create.queue";
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create";

    public static final String ORDER_EXPIRE_EXCHANGE = "order.expire.exchange";
    public static final String ORDER_HOLD_TTL_QUEUE = "order.hold.ttl.queue";
    public static final String ORDER_EXPIRE_QUEUE = "order.expire.queue";
    public static final String ORDER_EXPIRE_ROUTING_KEY = "order.expire";

    public static final String OUTBOX_EXCHANGE = "ticketing.events.exchange";
    public static final String OUTBOX_ORDER_CREATED_QUEUE = "ticketing.events.order-created.queue";
    public static final String OUTBOX_ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String OUTBOX_ORDER_EXPIRED_QUEUE = "ticketing.events.order-expired.queue";
    public static final String OUTBOX_ORDER_EXPIRED_ROUTING_KEY = "order.expired";
    public static final String OUTBOX_PAYMENT_SUCCESS_QUEUE = "ticketing.events.payment-success.queue";
    public static final String OUTBOX_PAYMENT_SUCCESS_ROUTING_KEY = "payment.success";

    @Bean
    DirectExchange orderExchange(MessagingProperties properties) {
        return new DirectExchange(properties.getOrderExchange(), true, false);
    }

    @Bean
    Queue orderCreateQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.getOrderCreateQueue()).build();
    }

    @Bean
    Binding orderCreateBinding(
            Queue orderCreateQueue,
            @Qualifier("orderExchange") DirectExchange orderExchange,
            MessagingProperties properties) {
        return BindingBuilder.bind(orderCreateQueue)
                .to(orderExchange)
                .with(properties.getOrderCreateRoutingKey());
    }

    @Bean
    DirectExchange orderExpireExchange(MessagingProperties properties) {
        return new DirectExchange(properties.getOrderExpireExchange(), true, false);
    }

    /**
     * The publisher will set per-message expiration from the reservation deadline. The queue only
     * provides the dead-letter route, so TicketTypes may use different hold durations.
     */
    @Bean
    Queue orderHoldTtlQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.getOrderHoldTtlQueue())
                .withArguments(Map.of(
                        "x-dead-letter-exchange", properties.getOrderExpireExchange(),
                        "x-dead-letter-routing-key", properties.getOrderExpireRoutingKey()))
                .build();
    }

    @Bean
    Binding orderHoldTtlBinding(
            Queue orderHoldTtlQueue,
            @Qualifier("orderExchange") DirectExchange orderExchange,
            MessagingProperties properties) {
        return BindingBuilder.bind(orderHoldTtlQueue)
                .to(orderExchange)
                .with(properties.getOrderHoldDelayRoutingKey());
    }

    @Bean
    Queue orderExpireQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.getOrderExpireQueue()).build();
    }

    @Bean
    Binding orderExpireBinding(
            Queue orderExpireQueue,
            @Qualifier("orderExpireExchange") DirectExchange orderExpireExchange,
            MessagingProperties properties) {
        return BindingBuilder.bind(orderExpireQueue)
                .to(orderExpireExchange)
                .with(properties.getOrderExpireRoutingKey());
    }

    @Bean
    DirectExchange outboxExchange(MessagingProperties properties) {
        return new DirectExchange(properties.getOutboxExchange(), true, false);
    }

    @Bean
    Queue outboxOrderCreatedQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.getOutboxOrderCreatedQueue()).build();
    }

    @Bean
    Binding outboxOrderCreatedBinding(
            Queue outboxOrderCreatedQueue,
            @Qualifier("outboxExchange") DirectExchange outboxExchange,
            MessagingProperties properties) {
        return BindingBuilder.bind(outboxOrderCreatedQueue)
                .to(outboxExchange)
                .with(properties.getOutboxOrderCreatedRoutingKey());
    }

    @Bean
    Queue outboxOrderExpiredQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.getOutboxOrderExpiredQueue()).build();
    }

    @Bean
    Binding outboxOrderExpiredBinding(
            Queue outboxOrderExpiredQueue,
            @Qualifier("outboxExchange") DirectExchange outboxExchange,
            MessagingProperties properties) {
        return BindingBuilder.bind(outboxOrderExpiredQueue)
                .to(outboxExchange)
                .with(properties.getOutboxOrderExpiredRoutingKey());
    }

    @Bean
    Queue outboxPaymentSuccessQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.getOutboxPaymentSuccessQueue()).build();
    }

    @Bean
    Binding outboxPaymentSuccessBinding(
            Queue outboxPaymentSuccessQueue,
            @Qualifier("outboxExchange") DirectExchange outboxExchange,
            MessagingProperties properties) {
        return BindingBuilder.bind(outboxPaymentSuccessQueue)
                .to(outboxExchange)
                .with(properties.getOutboxPaymentSuccessRoutingKey());
    }

    @Bean
    SimpleRabbitListenerContainerFactory orderCreateListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(true);
        return factory;
    }
}

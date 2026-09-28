package com.tienphat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ticketing.messaging")
public class MessagingProperties {

    private boolean enabled = true;
    private String orderExchange = RabbitTopologyConfig.ORDER_EXCHANGE;
    private String orderCreateQueue = RabbitTopologyConfig.ORDER_CREATE_QUEUE;
    private String orderCreateRoutingKey = RabbitTopologyConfig.ORDER_CREATE_ROUTING_KEY;
    private String orderExpireExchange = RabbitTopologyConfig.ORDER_EXPIRE_EXCHANGE;
    private String orderHoldTtlQueue = RabbitTopologyConfig.ORDER_HOLD_TTL_QUEUE;
    private String orderHoldDelayRoutingKey = RabbitTopologyConfig.ORDER_CREATE_ROUTING_KEY + ".delay";
    private String orderExpireQueue = RabbitTopologyConfig.ORDER_EXPIRE_QUEUE;
    private String orderExpireRoutingKey = RabbitTopologyConfig.ORDER_EXPIRE_ROUTING_KEY;
    private Duration reservationIntentRetryDelay = Duration.ofSeconds(5);
    private Duration reconciliationInterval = Duration.ofMinutes(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getOrderExchange() {
        return orderExchange;
    }

    public void setOrderExchange(String orderExchange) {
        this.orderExchange = orderExchange;
    }

    public String getOrderCreateQueue() {
        return orderCreateQueue;
    }

    public void setOrderCreateQueue(String orderCreateQueue) {
        this.orderCreateQueue = orderCreateQueue;
    }

    public String getOrderCreateRoutingKey() {
        return orderCreateRoutingKey;
    }

    public void setOrderCreateRoutingKey(String orderCreateRoutingKey) {
        this.orderCreateRoutingKey = orderCreateRoutingKey;
    }

    public String getOrderExpireExchange() {
        return orderExpireExchange;
    }

    public void setOrderExpireExchange(String orderExpireExchange) {
        this.orderExpireExchange = orderExpireExchange;
    }

    public String getOrderHoldTtlQueue() {
        return orderHoldTtlQueue;
    }

    public void setOrderHoldTtlQueue(String orderHoldTtlQueue) {
        this.orderHoldTtlQueue = orderHoldTtlQueue;
    }

    public String getOrderHoldDelayRoutingKey() {
        return orderHoldDelayRoutingKey;
    }

    public void setOrderHoldDelayRoutingKey(String orderHoldDelayRoutingKey) {
        this.orderHoldDelayRoutingKey = orderHoldDelayRoutingKey;
    }

    public String getOrderExpireQueue() {
        return orderExpireQueue;
    }

    public void setOrderExpireQueue(String orderExpireQueue) {
        this.orderExpireQueue = orderExpireQueue;
    }

    public String getOrderExpireRoutingKey() {
        return orderExpireRoutingKey;
    }

    public void setOrderExpireRoutingKey(String orderExpireRoutingKey) {
        this.orderExpireRoutingKey = orderExpireRoutingKey;
    }

    public Duration getReservationIntentRetryDelay() {
        return reservationIntentRetryDelay;
    }

    public void setReservationIntentRetryDelay(Duration reservationIntentRetryDelay) {
        this.reservationIntentRetryDelay = reservationIntentRetryDelay;
    }

    public Duration getReconciliationInterval() {
        return reconciliationInterval;
    }

    public void setReconciliationInterval(Duration reconciliationInterval) {
        this.reconciliationInterval = reconciliationInterval;
    }
}

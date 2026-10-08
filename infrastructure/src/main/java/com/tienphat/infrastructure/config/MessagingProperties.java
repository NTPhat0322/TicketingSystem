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
    private String outboxExchange = RabbitTopologyConfig.OUTBOX_EXCHANGE;
    private String outboxOrderCreatedQueue = RabbitTopologyConfig.OUTBOX_ORDER_CREATED_QUEUE;
    private String outboxOrderCreatedRoutingKey = RabbitTopologyConfig.OUTBOX_ORDER_CREATED_ROUTING_KEY;
    private String outboxOrderExpiredQueue = RabbitTopologyConfig.OUTBOX_ORDER_EXPIRED_QUEUE;
    private String outboxOrderExpiredRoutingKey = RabbitTopologyConfig.OUTBOX_ORDER_EXPIRED_ROUTING_KEY;
    private String outboxPaymentSuccessQueue = RabbitTopologyConfig.OUTBOX_PAYMENT_SUCCESS_QUEUE;
    private String outboxPaymentSuccessRoutingKey = RabbitTopologyConfig.OUTBOX_PAYMENT_SUCCESS_ROUTING_KEY;
    private boolean outboxPublisherEnabled = true;
    private Duration outboxPublisherInterval = Duration.ofSeconds(1);
    private int outboxBatchSize = 50;
    private boolean relayEnabled = true;
    private Duration relayInterval = Duration.ofSeconds(1);
    private int relayBatchSize = 50;
    private Duration relayClaimLease = Duration.ofSeconds(15);
    private Duration reservationIntentRetryDelay = Duration.ofSeconds(5);
    private Duration reservationIntentMaxRetryDelay = Duration.ofMinutes(1);
    private Duration publisherConfirmTimeout = Duration.ofSeconds(5);
    private Duration reconciliationInterval = Duration.ofMinutes(1);
    private Duration orderCreationGracePeriod = Duration.ofSeconds(10);
    private int reconciliationBatchSize = 100;

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

    public String getOutboxExchange() {
        return outboxExchange;
    }

    public void setOutboxExchange(String outboxExchange) {
        this.outboxExchange = outboxExchange;
    }

    public String getOutboxOrderCreatedQueue() {
        return outboxOrderCreatedQueue;
    }

    public void setOutboxOrderCreatedQueue(String outboxOrderCreatedQueue) {
        this.outboxOrderCreatedQueue = outboxOrderCreatedQueue;
    }

    public String getOutboxOrderCreatedRoutingKey() {
        return outboxOrderCreatedRoutingKey;
    }

    public void setOutboxOrderCreatedRoutingKey(String outboxOrderCreatedRoutingKey) {
        this.outboxOrderCreatedRoutingKey = outboxOrderCreatedRoutingKey;
    }

    public String getOutboxOrderExpiredQueue() {
        return outboxOrderExpiredQueue;
    }

    public void setOutboxOrderExpiredQueue(String outboxOrderExpiredQueue) {
        this.outboxOrderExpiredQueue = outboxOrderExpiredQueue;
    }

    public String getOutboxOrderExpiredRoutingKey() {
        return outboxOrderExpiredRoutingKey;
    }

    public void setOutboxOrderExpiredRoutingKey(String outboxOrderExpiredRoutingKey) {
        this.outboxOrderExpiredRoutingKey = outboxOrderExpiredRoutingKey;
    }

    public String getOutboxPaymentSuccessQueue() {
        return outboxPaymentSuccessQueue;
    }

    public void setOutboxPaymentSuccessQueue(String outboxPaymentSuccessQueue) {
        this.outboxPaymentSuccessQueue = outboxPaymentSuccessQueue;
    }

    public String getOutboxPaymentSuccessRoutingKey() {
        return outboxPaymentSuccessRoutingKey;
    }

    public void setOutboxPaymentSuccessRoutingKey(String outboxPaymentSuccessRoutingKey) {
        this.outboxPaymentSuccessRoutingKey = outboxPaymentSuccessRoutingKey;
    }

    public boolean isOutboxPublisherEnabled() {
        return outboxPublisherEnabled;
    }

    public void setOutboxPublisherEnabled(boolean outboxPublisherEnabled) {
        this.outboxPublisherEnabled = outboxPublisherEnabled;
    }

    public Duration getOutboxPublisherInterval() {
        return outboxPublisherInterval;
    }

    public void setOutboxPublisherInterval(Duration outboxPublisherInterval) {
        this.outboxPublisherInterval = outboxPublisherInterval;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    public boolean isRelayEnabled() {
        return relayEnabled;
    }

    public void setRelayEnabled(boolean relayEnabled) {
        this.relayEnabled = relayEnabled;
    }

    public Duration getRelayInterval() {
        return relayInterval;
    }

    public void setRelayInterval(Duration relayInterval) {
        this.relayInterval = relayInterval;
    }

    public int getRelayBatchSize() {
        return relayBatchSize;
    }

    public void setRelayBatchSize(int relayBatchSize) {
        this.relayBatchSize = relayBatchSize;
    }

    public Duration getRelayClaimLease() {
        return relayClaimLease;
    }

    public void setRelayClaimLease(Duration relayClaimLease) {
        this.relayClaimLease = relayClaimLease;
    }

    public Duration getReservationIntentRetryDelay() {
        return reservationIntentRetryDelay;
    }

    public void setReservationIntentRetryDelay(Duration reservationIntentRetryDelay) {
        this.reservationIntentRetryDelay = reservationIntentRetryDelay;
    }

    public Duration getReservationIntentMaxRetryDelay() {
        return reservationIntentMaxRetryDelay;
    }

    public void setReservationIntentMaxRetryDelay(Duration reservationIntentMaxRetryDelay) {
        this.reservationIntentMaxRetryDelay = reservationIntentMaxRetryDelay;
    }

    public Duration getPublisherConfirmTimeout() {
        return publisherConfirmTimeout;
    }

    public void setPublisherConfirmTimeout(Duration publisherConfirmTimeout) {
        this.publisherConfirmTimeout = publisherConfirmTimeout;
    }

    public Duration getReconciliationInterval() {
        return reconciliationInterval;
    }

    public void setReconciliationInterval(Duration reconciliationInterval) {
        this.reconciliationInterval = reconciliationInterval;
    }

    public Duration getOrderCreationGracePeriod() {
        return orderCreationGracePeriod;
    }

    public void setOrderCreationGracePeriod(Duration orderCreationGracePeriod) {
        this.orderCreationGracePeriod = orderCreationGracePeriod;
    }

    public int getReconciliationBatchSize() {
        return reconciliationBatchSize;
    }

    public void setReconciliationBatchSize(int reconciliationBatchSize) {
        this.reconciliationBatchSize = reconciliationBatchSize;
    }
}

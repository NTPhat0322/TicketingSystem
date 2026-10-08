package com.tienphat.infrastructure.messaging;

import com.tienphat.application.order.CreateOrderFromReservationUseCase;
import com.tienphat.application.order.ExpireOrderTransaction;
import com.tienphat.application.order.ExpireOrderUseCase;
import com.tienphat.application.payment.InitiatePaymentUseCase;
import com.tienphat.application.payment.PaymentGatewayPort;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.port.ReservationRequest;
import com.tienphat.domain.port.ReservationResult;
import com.tienphat.domain.port.StockCachePort;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.OutboxEventRepository;
import com.tienphat.domain.repository.PaymentRepository;
import com.tienphat.domain.vo.Money;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import com.tienphat.infrastructure.config.RabbitTopologyConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfrastructureTestApplication.class)
@Import(OrderExpiryMessagingIntegrationTest.ExpiryTestConfiguration.class)
@TestPropertySource(properties = {
        "ticketing.messaging.relay-enabled=true",
        "ticketing.messaging.consumers-enabled=true",
        "ticketing.messaging.relay-interval=1h",
        "ticketing.messaging.reconciliation-interval=1h"
})
class OrderExpiryMessagingIntegrationTest extends AbstractRedisRabbitIntegrationTest {

    @Autowired
    private StockCachePort stockCachePort;

    @Autowired
    private ReservationIntentStore intentStore;

    @Autowired
    private ReservationIntentRelay relay;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderExpiryMessageCodec expiryCodec;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void ttlAndDlxExpireUnpaidOrderAndReplayReleasesOnlyOnce() {
        ReservationRequest request = request(Duration.ofSeconds(2));
        stockCachePort.warmUp(request.ticketTypeId(), 3);
        assertThat(stockCachePort.tryReserve(request)).isEqualTo(ReservationResult.SUCCESS);
        ReservationIntent snapshot = intentStore.findByOrderId(request.orderId()).orElseThrow();

        assertThat(relay.relayOnce()).isEqualTo(1);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(orderRepository.findById(request.orderId())).hasValueSatisfying(order ->
                        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT)));
        assertThat(stockCachePort.getAvailableStock(request.ticketTypeId())).isEqualTo(2);

        Awaitility.await().atMost(Duration.ofSeconds(12)).untilAsserted(() -> {
            assertThat(orderRepository.findById(request.orderId())).hasValueSatisfying(order ->
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED));
            assertThat(stockCachePort.getAvailableStock(request.ticketTypeId())).isEqualTo(3);
            assertThat(intentStore.findByOrderId(request.orderId()).orElseThrow().state())
                    .isEqualTo(ReservationIntentState.EXPIRED);
            assertThat(outboxCount(request.orderId())).isEqualTo(2);
        });

        OrderExpiryMessage replay = OrderExpiryMessage.fromIntent(snapshot);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
        rabbitTemplate.send(
                RabbitTopologyConfig.ORDER_EXPIRE_EXCHANGE,
                RabbitTopologyConfig.ORDER_EXPIRE_ROUTING_KEY,
                new Message(expiryCodec.encode(replay), properties));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(stockCachePort.getAvailableStock(request.ticketTypeId())).isEqualTo(3);
            assertThat(outboxCount(request.orderId())).isEqualTo(2);
        });
    }

    @Test
    void ttlExpiryDoesNotReleaseStockAfterPaymentWins() {
        ReservationRequest request = request(Duration.ofSeconds(2));
        stockCachePort.warmUp(request.ticketTypeId(), 3);
        assertThat(stockCachePort.tryReserve(request)).isEqualTo(ReservationResult.SUCCESS);
        assertThat(relay.relayOnce()).isEqualTo(1);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(orderRepository.findById(request.orderId())).hasValueSatisfying(order ->
                        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT)));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Order order = orderRepository.findByIdForUpdate(request.orderId()).orElseThrow();
            order.pay();
            orderRepository.save(order);
        });

        Awaitility.await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                assertThat(orderRepository.findById(request.orderId())).hasValueSatisfying(order ->
                        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)));
        assertThat(stockCachePort.getAvailableStock(request.ticketTypeId())).isEqualTo(2);
        assertThat(outboxCount(request.orderId())).isEqualTo(1);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(intentStore.findByOrderId(request.orderId()).orElseThrow().state())
                        .isEqualTo(ReservationIntentState.COMPLETED));
    }

    private ReservationRequest request(Duration holdDuration) {
        Instant reservedAt = Instant.now();
        return new ReservationRequest(
                Order.generateId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                Money.of(new BigDecimal("150000")),
                3,
                Math.toIntExact(holdDuration.toSeconds()),
                reservedAt,
                reservedAt.plus(holdDuration));
    }

    private int outboxCount(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ?",
                Integer.class,
                orderId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExpiryTestConfiguration {

        @Bean
        CreateOrderFromReservationUseCase createOrderFromReservationUseCase(
                OrderRepository orderRepository,
                OutboxEventRepository outboxEventRepository,
                InitiatePaymentUseCase initiatePaymentUseCase) {
            return new CreateOrderFromReservationUseCase(
                    orderRepository, outboxEventRepository, initiatePaymentUseCase);
        }

        @Bean
        InitiatePaymentUseCase initiatePaymentUseCase(
                OrderRepository orderRepository,
                PaymentRepository paymentRepository,
                PaymentGatewayPort paymentGatewayPort) {
            return new InitiatePaymentUseCase(orderRepository, paymentRepository, paymentGatewayPort);
        }

        @Bean
        ExpireOrderTransaction expireOrderTransaction(
                OrderRepository orderRepository,
                OutboxEventRepository outboxEventRepository) {
            return new ExpireOrderTransaction(orderRepository, outboxEventRepository);
        }

        @Bean
        ExpireOrderUseCase expireOrderUseCase(
                ExpireOrderTransaction transaction,
                ReservationIntentStore intentStore,
                StockCachePort stockCachePort) {
            return new ExpireOrderUseCase(transaction, intentStore, stockCachePort);
        }
    }
}

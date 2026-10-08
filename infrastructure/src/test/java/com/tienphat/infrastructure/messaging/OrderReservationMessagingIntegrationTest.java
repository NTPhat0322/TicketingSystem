package com.tienphat.infrastructure.messaging;

import com.tienphat.application.order.CreateOrderFromReservationUseCase;
import com.tienphat.application.order.ExpireOrderTransaction;
import com.tienphat.application.order.ExpireOrderUseCase;
import com.tienphat.application.payment.InitiatePaymentUseCase;
import com.tienphat.application.payment.PaymentGatewayPort;
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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.awaitility.Awaitility;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfrastructureTestApplication.class)
@Import(OrderReservationMessagingIntegrationTest.MessagingTestConfiguration.class)
@TestPropertySource(properties = {
        "ticketing.messaging.relay-enabled=true",
        "ticketing.messaging.consumers-enabled=true",
        "ticketing.messaging.relay-interval=1h"
})
class OrderReservationMessagingIntegrationTest extends AbstractRedisRabbitIntegrationTest {

    @Autowired
    private StockCachePort stockCachePort;

    @Autowired
    private ReservationIntentStore intentStore;

    @Autowired
    private ReservationIntentRelay relay;

    @Autowired
    private OrderCreatePublisher publisher;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void redisReservationRelaysToRabbitAndCreatesOneOrderAndOutboxEvent() {
        UUID orderId = com.tienphat.domain.model.Order.generateId();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        Instant reservedAt = Instant.now().minusSeconds(1);
        Instant expiresAt = reservedAt.plusSeconds(300);
        ReservationRequest request = new ReservationRequest(
                orderId, userId, eventId, ticketTypeId, 2,
                Money.of(new BigDecimal("150000")), 4, 300, reservedAt, expiresAt);

        stockCachePort.warmUp(ticketTypeId, 10);
        assertThat(stockCachePort.tryReserve(request)).isEqualTo(ReservationResult.SUCCESS);
        ReservationIntent intent = intentStore.findByOrderId(orderId).orElseThrow();

        assertThat(relay.relayOnce()).isEqualTo(1);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var order = orderRepository.findById(orderId);
            assertThat(order).isPresent();
            if (order.isPresent()) {
                assertThat(order.get().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
                assertThat(order.get().getItems()).hasSize(1);
                assertThat(order.get().getItems().get(0).getQuantity()).isEqualTo(2);
                assertThat(order.get().getItems().get(0).getUnitPrice())
                        .isEqualTo(Money.of(new BigDecimal("150000")));
            }
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from outbox_events where aggregate_id = ?",
                    Integer.class,
                    orderId)).isEqualTo(1);
            assertThat(paymentRepository.findByOrderId(orderId)).hasValueSatisfying(payment -> {
                assertThat(payment.getProvider()).isEqualTo(com.tienphat.domain.model.PaymentProvider.LOCAL);
                assertThat(payment.getTransactionRef()).isEqualTo("LOCAL-" + orderId);
                assertThat(payment.getStatus()).isEqualTo(com.tienphat.domain.model.PaymentStatus.PENDING);
            });
        });

        assertThat(intentStore.findByOrderId(orderId).orElseThrow().state())
                .isEqualTo(ReservationIntentState.ORDER_CREATED);
        assertThat(stockCachePort.getAvailableStock(ticketTypeId)).isEqualTo(8);

        // Replay the same reservation snapshot: the worker must acknowledge it without another row.
        for (int replay = 0; replay < 5; replay++) {
            publisher.publish(intent);
        }
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                jdbcTemplate.queryForObject(
                        "select count(*) from orders where id = ?", Integer.class, orderId))
                .isEqualTo(1));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from order_items where order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payments where order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ?",
                Integer.class,
                orderId)).isEqualTo(1);
        assertThat(stockCachePort.getAvailableStock(ticketTypeId)).isEqualTo(8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MessagingTestConfiguration {

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

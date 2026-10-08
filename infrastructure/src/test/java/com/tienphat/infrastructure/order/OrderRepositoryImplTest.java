package com.tienphat.infrastructure.order;

import com.tienphat.domain.exception.InvalidOrderStateException;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.vo.Money;
import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class OrderRepositoryImplTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderRepositoryImpl orderRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void savesAndLoadsOrderWithEveryFieldAndCascadedItems() {
        Order order = newOrder("ORD-" + UUID.randomUUID());
        order.addItem(UUID.randomUUID(), 2, Money.of(new BigDecimal("125.50")));

        orderRepository.save(order);
        Order found = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(found.getId()).isEqualTo(order.getId());
        assertThat(found.getOrderCode()).isEqualTo(order.getOrderCode());
        assertThat(found.getUserId()).isEqualTo(order.getUserId());
        assertThat(found.getEventId()).isEqualTo(order.getEventId());
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(found.getTotalAmount()).isEqualTo(order.getTotalAmount());
        assertThat(found.getReservedAt()).isEqualTo(order.getReservedAt());
        assertThat(found.getExpiresAt()).isEqualTo(order.getExpiresAt());
        assertThat(found.getCreatedAt()).isCloseTo(order.getCreatedAt(), within(2, ChronoUnit.MICROS));
        assertThat(found.getUpdatedAt()).isCloseTo(order.getUpdatedAt(), within(2, ChronoUnit.MICROS));
        assertThat(found.getItems()).usingRecursiveComparison().isEqualTo(order.getItems());
    }

    @Test
    void duplicateOrderCodeIsRejectedByDatabaseConstraint() {
        String orderCode = "ORD-" + UUID.randomUUID();
        orderRepository.save(newOrder(orderCode));

        assertThatThrownBy(() -> orderRepository.save(newOrder(orderCode)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingOrderCascadesToOrderItems() {
        Order order = newOrder("ORD-" + UUID.randomUUID());
        order.addItem(UUID.randomUUID(), 1, Money.of(new BigDecimal("50.00")));
        UUID itemId = order.getItems().get(0).getId();
        orderRepository.save(order);

        orderJpaRepository.deleteById(order.getId());

        assertThat(orderJpaRepository.findById(order.getId())).isEmpty();
        Number itemCount = (Number) entityManager.createNativeQuery(
                        "select count(*) from order_items where id = :itemId")
                .setParameter("itemId", itemId)
                .getSingleResult();
        assertThat(itemCount.longValue()).isZero();
    }

    @Test
    void findByIdForUpdateSerializesPaymentAndExpiryTransitions() throws Exception {
        Order order = newOrder("ORD-" + UUID.randomUUID());
        orderRepository.save(order);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        Thread first = new Thread(() -> transactionTemplate.executeWithoutResult(status -> {
            try {
                Order locked = orderRepository.findByIdForUpdate(order.getId()).orElseThrow();
                firstLocked.countDown();
                assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
                locked.pay();
                orderRepository.save(locked);
            } catch (Throwable failure) {
                firstFailure.set(failure);
                status.setRollbackOnly();
            }
        }));

        Thread second = new Thread(() -> transactionTemplate.executeWithoutResult(status -> {
            try {
                assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
                Order locked = orderRepository.findByIdForUpdate(order.getId()).orElseThrow();
                locked.expire();
                orderRepository.save(locked);
            } catch (Throwable failure) {
                secondFailure.set(failure);
                status.setRollbackOnly();
            }
        }));

        first.start();
        assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
        second.start();
        Thread.sleep(150);
        assertThat(second.isAlive()).as("expiry must wait for the payment row lock").isTrue();
        releaseFirst.countDown();
        first.join(TimeUnit.SECONDS.toMillis(5));
        second.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(firstFailure).hasValue(null);
        assertThat(secondFailure.get()).isInstanceOf(InvalidOrderStateException.class);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    private static Order newOrder(String orderCode) {
        Instant reservedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return Order.create(
                Order.generateId(),
                orderCode,
                UUID.randomUUID(),
                UUID.randomUUID(),
                reservedAt,
                reservedAt.plusSeconds(300));
    }
}

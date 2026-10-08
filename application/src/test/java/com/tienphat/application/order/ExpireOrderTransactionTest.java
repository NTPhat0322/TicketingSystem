package com.tienphat.application.order;

import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.OutboxEventRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpireOrderTransactionTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:10:00Z");
    private static final UUID ORDER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final Money UNIT_PRICE = Money.of(new BigDecimal("150000"));
    private static final Instant RESERVED_AT = NOW.minusSeconds(300);
    private static final Instant EXPIRES_AT = NOW.minusSeconds(1);

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final ExpireOrderTransaction transaction = new ExpireOrderTransaction(
            orderRepository,
            outboxEventRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void expiresPendingOrderAndRecordsOneOutboxEvent() {
        Order order = order();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpireOrderResult result = transaction.execute(command());

        assertThat(result.outcome()).isEqualTo(ExpiryOutcome.EXPIRED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(result.releaseReservation().quantity()).isEqualTo(2);
        verify(orderRepository).save(order);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void paidOrderIsAcknowledgeableWithoutOutboxOrRelease() {
        Order order = order();
        order.pay();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        ExpireOrderResult result = transaction.execute(command());

        assertThat(result.outcome()).isEqualTo(ExpiryOutcome.PAID);
        assertThat(result.releaseReservation()).isNull();
        verify(orderRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void missingOrderIsReturnedForIntentBasedReconciliation() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        ExpireOrderResult result = transaction.execute(command());

        assertThat(result).isEqualTo(new ExpireOrderResult(ORDER_ID, ExpiryOutcome.ORDER_MISSING, null));
        verify(orderRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void futureDeadlineDoesNotExpireEarly() {
        Instant future = NOW.plusSeconds(30);
        Order order = Order.create(
                ORDER_ID, "ORD-" + ORDER_ID, USER_ID, EVENT_ID, NOW.minusSeconds(10), future);
        order.addItem(TICKET_TYPE_ID, 2, UNIT_PRICE);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        ExpireOrderCommand futureCommand = new ExpireOrderCommand(
                UUID.randomUUID(), ORDER_ID, USER_ID, EVENT_ID, TICKET_TYPE_ID,
                2, UNIT_PRICE, NOW.minusSeconds(10), future);

        assertThat(transaction.execute(futureCommand).outcome()).isEqualTo(ExpiryOutcome.NOT_DUE);
        verify(orderRepository, never()).save(any());
    }

    private static Order order() {
        Order order = Order.create(ORDER_ID, "ORD-" + ORDER_ID, USER_ID, EVENT_ID,
                RESERVED_AT, EXPIRES_AT);
        order.addItem(TICKET_TYPE_ID, 2, UNIT_PRICE);
        return order;
    }

    private static ExpireOrderCommand command() {
        return new ExpireOrderCommand(
                UUID.randomUUID(), ORDER_ID, USER_ID, EVENT_ID, TICKET_TYPE_ID,
                2, UNIT_PRICE, RESERVED_AT, EXPIRES_AT);
    }
}

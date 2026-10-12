package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.exception.InvalidOrderStateException;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.PaymentRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CancelOrderTransactionTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    private static final UUID ORDER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final Money UNIT_PRICE = Money.of(new BigDecimal("150000"));
    private static final AuthorizationContext OWNER = new AuthorizationContext(USER_ID, UserRole.CUSTOMER);

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final CancelOrderTransaction transaction = new CancelOrderTransaction(orderRepository, paymentRepository);

    @Test
    void cancelsPendingOrderAndReturnsReleaseCoordinates() {
        Order order = order();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CancelOrderTransactionResult result = transaction.execute(new CancelOrderCommand(ORDER_ID, OWNER)).orElseThrow();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.order().status()).isEqualTo(OrderTrackingStatus.CANCELLED);
        assertThat(result.release()).isEqualTo(
                new ExpireOrderResult.ReleaseReservation(ORDER_ID, TICKET_TYPE_ID, USER_ID, 2));
        verify(orderRepository).save(order);
        verify(paymentRepository).findByOrderId(ORDER_ID);
    }

    @Test
    void alreadyCancelledOrderIsReturnedWithoutWriteSoReleaseCanBeRetried() {
        Order order = order();
        order.cancel();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        CancelOrderTransactionResult result = transaction.execute(new CancelOrderCommand(ORDER_ID, OWNER)).orElseThrow();

        assertThat(result.order().status()).isEqualTo(OrderTrackingStatus.CANCELLED);
        assertThat(result.release().quantity()).isEqualTo(2);
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "EXPIRED", "FAILED"})
    void otherTerminalStatusIsRejectedWithoutWrite(OrderStatus status) {
        Order order = orderIn(status);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> transaction.execute(new CancelOrderCommand(ORDER_ID, OWNER)))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining(status.name());

        assertThat(order.getStatus()).isEqualTo(status);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void foreignUserIsRejectedBeforeAnyWrite() {
        Order order = order();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> transaction.execute(new CancelOrderCommand(ORDER_ID,
                new AuthorizationContext(UUID.randomUUID(), UserRole.CUSTOMER))))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void adminCanCancelAnyOrder() {
        Order order = order();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transaction.execute(new CancelOrderCommand(ORDER_ID, new AuthorizationContext(UUID.randomUUID(), UserRole.ADMIN)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void missingOrderReturnsEmptyForIntentFallback() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        assertThat(transaction.execute(new CancelOrderCommand(ORDER_ID, OWNER))).isEmpty();
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(paymentRepository);
    }

    private static Order order() {
        Order order = Order.create(ORDER_ID, "ORD-" + ORDER_ID, USER_ID, EVENT_ID, NOW, NOW.plusSeconds(300));
        order.addItem(TICKET_TYPE_ID, 2, UNIT_PRICE);
        return order;
    }

    private static Order orderIn(OrderStatus status) {
        Order order = order();
        switch (status) {
            case PAID -> order.pay();
            case EXPIRED -> order.expire();
            case FAILED -> order.fail();
            default -> throw new IllegalArgumentException(status.name());
        }
        return order;
    }
}

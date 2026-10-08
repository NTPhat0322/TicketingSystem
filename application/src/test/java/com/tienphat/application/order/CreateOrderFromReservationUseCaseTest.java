package com.tienphat.application.order;

import com.tienphat.application.payment.InitiatePaymentUseCase;
import com.tienphat.domain.exception.InvalidOrderDataException;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.OutboxEventRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.when;

class CreateOrderFromReservationUseCaseTest {

    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final Instant RESERVED_AT = Instant.parse("2026-09-28T08:00:00.123456Z");
    private static final Instant EXPIRES_AT = RESERVED_AT.plusSeconds(300);
    private static final Money UNIT_PRICE = Money.of(new BigDecimal("150000"));

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final InitiatePaymentUseCase initiatePaymentUseCase = mock(InitiatePaymentUseCase.class);
    private final CreateOrderFromReservationUseCase useCase =
            new CreateOrderFromReservationUseCase(
                    orderRepository, outboxEventRepository, initiatePaymentUseCase);

    @Test
    void createsPendingOrderAndOneOrderCreatedOutboxEvent() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderCreationResult result = useCase.execute(command());

        assertThat(result).isEqualTo(new OrderCreationResult(ORDER_ID, true));
        var orderCaptor = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(ORDER_ID);
        assertThat(saved.getOrderCode()).isEqualTo("ORD-" + ORDER_ID.toString().toUpperCase());
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getTicketTypeId()).isEqualTo(TICKET_TYPE_ID);
        assertThat(saved.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(saved.getTotalAmount()).isEqualTo(Money.of(new BigDecimal("300000")));

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAggregateId()).isEqualTo(ORDER_ID);
        assertThat(eventCaptor.getValue().getPayload())
                .contains(MESSAGE_ID.toString())
                .contains(ORDER_ID.toString())
                .contains("\"quantity\":2");
        verify(initiatePaymentUseCase).execute(any());
    }

    @Test
    void duplicateDeliveryReturnsNoOpAndDoesNotWriteAnotherOrderOrOutbox() {
        Order existing = Order.create(ORDER_ID, "ORD-" + ORDER_ID.toString().toUpperCase(), USER_ID, EVENT_ID,
                RESERVED_AT, EXPIRES_AT);
        existing.addItem(TICKET_TYPE_ID, 2, UNIT_PRICE);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));

        OrderCreationResult result = useCase.execute(command());

        assertThat(result).isEqualTo(new OrderCreationResult(ORDER_ID, false));
        verify(orderRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void duplicateDeliveryAfterPaymentRemainsIdempotent() {
        Order existing = Order.create(ORDER_ID, "ORD-" + ORDER_ID.toString().toUpperCase(), USER_ID, EVENT_ID,
                RESERVED_AT, EXPIRES_AT);
        existing.addItem(TICKET_TYPE_ID, 2, UNIT_PRICE);
        existing.pay();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));

        assertThat(useCase.execute(command()).created()).isFalse();
        verify(initiatePaymentUseCase, never()).execute(any());
    }

    @Test
    void sameOrderIdWithDifferentSnapshotIsRejected() {
        Order existing = Order.create(ORDER_ID, "ORD-" + ORDER_ID.toString().toUpperCase(), USER_ID, EVENT_ID,
                RESERVED_AT, EXPIRES_AT);
        existing.addItem(TICKET_TYPE_ID, 1, UNIT_PRICE);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(InvalidOrderDataException.class);
    }

    private static CreateOrderFromReservationCommand command() {
        return new CreateOrderFromReservationCommand(
                MESSAGE_ID, ORDER_ID, USER_ID, EVENT_ID, TICKET_TYPE_ID,
                2, UNIT_PRICE, RESERVED_AT, EXPIRES_AT);
    }
}

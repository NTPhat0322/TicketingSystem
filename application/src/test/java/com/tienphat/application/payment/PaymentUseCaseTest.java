package com.tienphat.application.payment;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.exception.PaymentOperationRejectedException;
import com.tienphat.domain.model.AggregateType;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.PaymentStatus;
import com.tienphat.domain.model.Ticket;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.port.StockCachePort;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.OutboxEventRepository;
import com.tienphat.domain.repository.PaymentRepository;
import com.tienphat.domain.repository.TicketRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

class PaymentUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final String TRANSACTION_REF = "LOCAL-transaction";
    private static final Money AMOUNT = Money.of(new BigDecimal("250000.00"));

    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private TicketTypeRepository ticketTypeRepository;
    private TicketRepository ticketRepository;
    private OutboxEventRepository outboxEventRepository;
    private ReservationIntentStore intentStore;
    private StockCachePort stockCachePort;
    private TicketCodeGenerator ticketCodeGenerator;
    private PaymentConfirmationTransaction transaction;
    private ConfirmPaymentUseCase confirmPayment;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        ticketTypeRepository = mock(TicketTypeRepository.class);
        ticketRepository = mock(TicketRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        intentStore = mock(ReservationIntentStore.class);
        stockCachePort = mock(StockCachePort.class);
        ticketCodeGenerator = (orderId, orderItemId, sequence) -> "TKT-" + sequence;
        transaction = new PaymentConfirmationTransaction(
                paymentRepository,
                orderRepository,
                ticketTypeRepository,
                ticketRepository,
                outboxEventRepository,
                ticketCodeGenerator);
        confirmPayment = new ConfirmPaymentUseCase(transaction, intentStore, stockCachePort);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void successMarksPaidConfirmsSaleIssuesTicketsAndWritesOutbox() {
        Order order = pendingOrder(2);
        Payment payment = pendingPayment(order);
        TicketType ticketType = ticketType(10);
        stubPending(payment, order, ticketType);

        PaymentCallbackResult result = confirmPayment.execute(callback(true, order.getTotalAmount(), USER_ID));

        assertThat(result.outcome()).isEqualTo(PaymentCallbackOutcome.SUCCESS);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.ticketCount()).isEqualTo(2);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(ticketType.getSoldQuantity()).isEqualTo(2);
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
        verify(ticketTypeRepository).save(ticketType);
        verify(ticketRepository, org.mockito.Mockito.times(2)).save(any(Ticket.class));

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAggregateType()).isEqualTo(AggregateType.PAYMENT);
        assertThat(eventCaptor.getValue().getEventType().name()).isEqualTo("PAYMENT_SUCCESS");
        assertThat(eventCaptor.getValue().getPayload()).contains(TRANSACTION_REF, "TKT-1", "TKT-2");
        verify(intentStore).markCompleted(order.getId());
        verifyNoInteractions(stockCachePort);
    }

    @Test
    void replayAfterSuccessIsNoOpAndKeepsStableResponse() {
        Order order = pendingOrder(2);
        order.pay();
        Payment payment = Payment.reconstitute(
                PAYMENT_ID, order.getId(), PaymentProvider.LOCAL, order.getTotalAmount(), PaymentStatus.SUCCESS,
                TRANSACTION_REF, Instant.now(), Instant.now());
        when(paymentRepository.findByTransactionRefForUpdate(TRANSACTION_REF)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        PaymentCallbackResult result = confirmPayment.execute(callback(true, AMOUNT.multiply(2), USER_ID));

        assertThat(result.outcome()).isEqualTo(PaymentCallbackOutcome.ALREADY_SUCCESS);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.ticketCount()).isEqualTo(2);
        verifyNoInteractions(ticketTypeRepository, ticketRepository, outboxEventRepository);
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(intentStore).markCompleted(order.getId());
    }

    @Test
    void failedCallbackFailsOrderAndReleasesRedisAfterDatabaseWork() {
        Order order = pendingOrder(1);
        Payment payment = pendingPayment(order);
        TicketType ticketType = ticketType(10);
        stubPending(payment, order, ticketType);

        PaymentCallbackResult result = confirmPayment.execute(callback(false, AMOUNT, USER_ID));

        assertThat(result.outcome()).isEqualTo(PaymentCallbackOutcome.FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
        verify(intentStore).markExpired(order.getId());
        verify(stockCachePort).release(order.getId(), TICKET_TYPE_ID, USER_ID, 1);
        verifyNoInteractions(ticketTypeRepository, ticketRepository, outboxEventRepository);
    }

    @Test
    void foreignOwnerCannotConfirmPayment() {
        Order order = pendingOrder(1);
        Payment payment = pendingPayment(order);
        stubPending(payment, order, ticketType(10));

        assertThatThrownBy(() -> confirmPayment.execute(callback(true, AMOUNT, OTHER_USER_ID)))
                .isExactlyInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(ticketTypeRepository, ticketRepository, outboxEventRepository,
                intentStore, stockCachePort);
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void amountMismatchCannotConfirmPayment() {
        Order order = pendingOrder(1);
        Payment payment = pendingPayment(order);
        stubPending(payment, order, ticketType(10));

        assertThatThrownBy(() -> confirmPayment.execute(
                callback(true, Money.of(new BigDecimal("1.00")), USER_ID)))
                .isExactlyInstanceOf(PaymentOperationRejectedException.class);

        verifyNoInteractions(ticketTypeRepository, ticketRepository, outboxEventRepository,
                intentStore, stockCachePort);
    }

    @Test
    void expiredOrderCannotBePaid() {
        Order order = pendingOrder(1);
        order.expire();
        Payment payment = pendingPayment(order);
        stubPending(payment, order, ticketType(10));

        assertThatThrownBy(() -> confirmPayment.execute(callback(true, AMOUNT, USER_ID)))
                .isExactlyInstanceOf(PaymentOperationRejectedException.class);

        verifyNoInteractions(ticketTypeRepository, ticketRepository, outboxEventRepository,
                intentStore, stockCachePort);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void initiationUsesOrderAmountAndIsIdempotentForPendingPayment() {
        Order order = pendingOrder(2);
        OrderRepository orderRepo = mock(OrderRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        PaymentGatewayPort gateway = request -> new PaymentGatewayPort.PaymentGatewayInitiation(
                PaymentProvider.LOCAL, TRANSACTION_REF);
        InitiatePaymentUseCase useCase = new InitiatePaymentUseCase(orderRepo, paymentRepo, gateway);
        Payment payment = pendingPayment(order);
        when(orderRepo.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderIdForUpdate(order.getId()))
                .thenReturn(Optional.empty(), Optional.of(payment));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentInitiationResult first = useCase.execute(
                new InitiatePaymentCommand(new AuthorizationContext(USER_ID, UserRole.CUSTOMER), order.getId()));
        PaymentInitiationResult second = useCase.execute(
                new InitiatePaymentCommand(new AuthorizationContext(USER_ID, UserRole.CUSTOMER), order.getId()));

        assertThat(first.amount()).isEqualByComparingTo("500000.00");
        assertThat(first.transactionRef()).isEqualTo(TRANSACTION_REF);
        assertThat(second.transactionRef()).isEqualTo(first.transactionRef());
        verify(paymentRepo).save(any(Payment.class));
    }

    @Test
    void initiationRejectsPendingPaymentForExpiredOrder() {
        Order order = pendingOrder(1);
        order.expire();
        Payment payment = pendingPayment(order);
        OrderRepository orderRepo = mock(OrderRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
        InitiatePaymentUseCase useCase = new InitiatePaymentUseCase(orderRepo, paymentRepo, gateway);
        when(orderRepo.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderIdForUpdate(order.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> useCase.execute(
                new InitiatePaymentCommand(new AuthorizationContext(USER_ID, UserRole.CUSTOMER), order.getId())))
                .isExactlyInstanceOf(PaymentOperationRejectedException.class);

        verifyNoInteractions(gateway);
        verify(paymentRepo, never()).save(any());
    }

    private void stubPending(Payment payment, Order order, TicketType ticketType) {
        when(paymentRepository.findByTransactionRefForUpdate(TRANSACTION_REF)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(ticketTypeRepository.findByIdForUpdate(TICKET_TYPE_ID)).thenReturn(Optional.of(ticketType));
    }

    private ConfirmPaymentCommand callback(boolean success, Money amount, UUID actorId) {
        return new ConfirmPaymentCommand(
                new AuthorizationContext(actorId, UserRole.CUSTOMER),
                TRANSACTION_REF,
                PaymentProvider.LOCAL,
                amount,
                success);
    }

    private Order pendingOrder(int quantity) {
        Order order = Order.create("ORD-TEST", USER_ID, EVENT_ID, 300);
        order.addItem(TICKET_TYPE_ID, quantity, AMOUNT);
        return order;
    }

    private Payment pendingPayment(Order order) {
        return Payment.reconstitute(
                PAYMENT_ID,
                order.getId(),
                PaymentProvider.LOCAL,
                order.getTotalAmount(),
                PaymentStatus.PENDING,
                TRANSACTION_REF,
                null,
                Instant.now());
    }

    private TicketType ticketType(int totalQuantity) {
        return TicketType.create(
                TICKET_TYPE_ID,
                EVENT_ID,
                "Standard",
                AMOUNT,
                totalQuantity,
                5,
                300);
    }
}

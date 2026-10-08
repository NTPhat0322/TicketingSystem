package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.exception.OrderNotFoundException;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.PaymentStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.ReservationIntentStatusPort;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.PaymentRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetOrderStatusUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final AuthorizationContext ACTOR = new AuthorizationContext(USER_ID, UserRole.CUSTOMER);
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final ReservationIntentStatusPort intentStatusPort = mock(ReservationIntentStatusPort.class);
    private GetOrderStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOrderStatusUseCase(orderRepository, paymentRepository, intentStatusPort);
    }

    @Test
    @DisplayName("execute() returns CREATING from the intent before the Order row exists")
    void execute_returnsCreatingFromIntent() {
        UUID orderId = Order.generateId();
        ReservationIntent intent = anIntent(orderId, USER_ID, ReservationIntentState.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        when(intentStatusPort.findByOrderId(orderId)).thenReturn(Optional.of(intent));

        OrderStatusResult result = useCase.execute(new GetOrderStatusCommand(orderId, ACTOR));

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderTrackingStatus.CREATING);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.quantity()).isEqualTo(2);
        assertThat(result.unitPrice()).isEqualByComparingTo("150000.00");
        assertThat(result.expiresAt()).isEqualTo(intent.expiresAt());
    }

    @Test
    @DisplayName("execute() returns the persisted Order lifecycle once the worker has created it")
    void execute_returnsPersistedOrderStatus() {
        UUID orderId = Order.generateId();
        Order order = Order.create(orderId, "ORD-0001", USER_ID, EVENT_ID,
                NOW, NOW.plusSeconds(300));
        order.addItem(TICKET_TYPE_ID, 2, Money.of(new BigDecimal("150000")));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(Payment.reconstitute(
                UUID.randomUUID(), orderId, PaymentProvider.LOCAL, order.getTotalAmount(),
                PaymentStatus.PENDING, "LOCAL-" + orderId, null, NOW)));

        OrderStatusResult result = useCase.execute(new GetOrderStatusCommand(orderId, ACTOR));

        assertThat(result.status()).isEqualTo(OrderTrackingStatus.PENDING_PAYMENT);
        assertThat(result.totalAmount()).isEqualByComparingTo("300000.00");
        assertThat(result.ticketTypeId()).isEqualTo(TICKET_TYPE_ID);
        assertThat(result.paymentProvider()).isEqualTo(PaymentProvider.LOCAL);
        assertThat(result.paymentTransactionRef()).isEqualTo("LOCAL-" + orderId);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("execute() maps a terminal intent without an Order row to EXPIRED")
    void execute_returnsExpiredForTerminalIntent() {
        UUID orderId = Order.generateId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        when(intentStatusPort.findByOrderId(orderId))
                .thenReturn(Optional.of(anIntent(orderId, USER_ID, ReservationIntentState.RELEASED)));

        OrderStatusResult result = useCase.execute(new GetOrderStatusCommand(orderId, ACTOR));

        assertThat(result.status()).isEqualTo(OrderTrackingStatus.EXPIRED);
    }

    @Test
    @DisplayName("execute() does not misreport a completed intent as expired")
    void execute_doesNotReportCompletedIntentAsExpired() {
        UUID orderId = Order.generateId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        when(intentStatusPort.findByOrderId(orderId))
                .thenReturn(Optional.of(anIntent(orderId, USER_ID, ReservationIntentState.COMPLETED)));

        OrderStatusResult result = useCase.execute(new GetOrderStatusCommand(orderId, ACTOR));

        assertThat(result.status()).isEqualTo(OrderTrackingStatus.CREATING);
    }

    @Test
    @DisplayName("execute() rejects a foreign persisted Order")
    void execute_rejectsForeignOrder() {
        UUID orderId = Order.generateId();
        Order order = Order.create(orderId, "ORD-0002", OTHER_USER_ID, EVENT_ID, 300);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(new GetOrderStatusCommand(orderId, ACTOR)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("execute() rejects a foreign intent while it is still CREATING")
    void execute_rejectsForeignIntent() {
        UUID orderId = Order.generateId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        when(intentStatusPort.findByOrderId(orderId))
                .thenReturn(Optional.of(anIntent(orderId, OTHER_USER_ID, ReservationIntentState.ENQUEUED)));

        assertThatThrownBy(() -> useCase.execute(new GetOrderStatusCommand(orderId, ACTOR)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("execute() returns a controlled not-found error when both sources are absent")
    void execute_throwsWhenOrderAndIntentAreMissing() {
        UUID orderId = Order.generateId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        when(intentStatusPort.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetOrderStatusCommand(orderId, ACTOR)))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private static ReservationIntent anIntent(UUID orderId, UUID userId, ReservationIntentState state) {
        return new ReservationIntent(orderId, userId, EVENT_ID, TICKET_TYPE_ID, 2,
                Money.of(new BigDecimal("150000")), 4, 300, NOW, NOW.plusSeconds(300), state, 0, NOW);
    }
}

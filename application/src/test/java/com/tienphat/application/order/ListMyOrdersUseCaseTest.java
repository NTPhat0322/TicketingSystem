package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.PaymentStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.domain.repository.PaymentRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListMyOrdersUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final AuthorizationContext ACTOR = new AuthorizationContext(USER_ID, UserRole.CUSTOMER);
    private static final PageRequest PAGE = new PageRequest(0, 20);
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private ListMyOrdersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListMyOrdersUseCase(orderRepository, paymentRepository);
    }

    @Test
    @DisplayName("execute() joins each order to its payment with one batch lookup and keeps page metadata")
    void execute_joinsPaymentsByOrderId() {
        Order paid = anOrder();
        Order unpaid = anOrder();
        Payment payment = Payment.reconstitute(
                UUID.randomUUID(), paid.getId(), PaymentProvider.LOCAL, paid.getTotalAmount(),
                PaymentStatus.SUCCESS, "LOCAL-" + paid.getId(), NOW, NOW);
        when(orderRepository.findAllByUserId(USER_ID, PAGE))
                .thenReturn(new PageResult<>(List.of(paid, unpaid), 0, 20, 42));
        when(paymentRepository.findAllByOrderIds(List.of(paid.getId(), unpaid.getId())))
                .thenReturn(List.of(payment));

        PageResult<OrderStatusResult> result = useCase.execute(new ListMyOrdersCommand(ACTOR, PAGE));

        assertThat(result.totalElements()).isEqualTo(42);
        assertThat(result.content()).extracting(OrderStatusResult::orderId)
                .containsExactly(paid.getId(), unpaid.getId());
        assertThat(result.content().get(0).paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.content().get(0).paymentTransactionRef()).isEqualTo("LOCAL-" + paid.getId());
        assertThat(result.content().get(1).paymentStatus()).isNull();
        assertThat(result.content().get(1).quantity()).isEqualTo(2);
        verify(paymentRepository).findAllByOrderIds(List.of(paid.getId(), unpaid.getId()));
    }

    @Test
    @DisplayName("execute() skips the payment lookup for an empty page")
    void execute_emptyPageSkipsPaymentLookup() {
        when(orderRepository.findAllByUserId(USER_ID, PAGE)).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        PageResult<OrderStatusResult> result = useCase.execute(new ListMyOrdersCommand(ACTOR, PAGE));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("execute() lists the ADMIN's own orders, not everyone's")
    void execute_adminSeesOwnOrdersOnly() {
        UUID adminId = UUID.randomUUID();
        when(orderRepository.findAllByUserId(adminId, PAGE)).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        useCase.execute(new ListMyOrdersCommand(new AuthorizationContext(adminId, UserRole.ADMIN), PAGE));

        verify(orderRepository).findAllByUserId(adminId, PAGE);
    }

    @Test
    @DisplayName("execute() rejects an unauthenticated caller before touching the repository")
    void execute_unauthenticatedIsForbidden() {
        assertThatThrownBy(() -> useCase.execute(
                new ListMyOrdersCommand(new AuthorizationContext(null, null), PAGE)))
                .isInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(orderRepository, paymentRepository);
    }

    private static Order anOrder() {
        Order order = Order.create(Order.generateId(), "ORD-" + UUID.randomUUID(), USER_ID, UUID.randomUUID(),
                NOW, NOW.plusSeconds(300));
        order.addItem(UUID.randomUUID(), 2, Money.of(new BigDecimal("150000")));
        return order;
    }
}

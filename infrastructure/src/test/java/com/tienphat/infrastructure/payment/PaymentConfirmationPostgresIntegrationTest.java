package com.tienphat.infrastructure.payment;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.payment.ConfirmPaymentCommand;
import com.tienphat.application.payment.PaymentCallbackOutcome;
import com.tienphat.application.payment.PaymentCallbackResult;
import com.tienphat.application.payment.PaymentConfirmationTransaction;
import com.tienphat.application.payment.UuidTicketCodeGenerator;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.PaymentStatus;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.OutboxEventRepository;
import com.tienphat.domain.repository.PaymentRepository;
import com.tienphat.domain.repository.TicketRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.tienphat.infrastructure.InfrastructureTestApplication.class)
class PaymentConfirmationPostgresIntegrationTest extends com.tienphat.infrastructure.AbstractPostgresIntegrationTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final String TRANSACTION_REF = "LOCAL-POSTGRES-" + UUID.randomUUID();
    private static final Money UNIT_PRICE = Money.of(new BigDecimal("125000.00"));

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional
    @Rollback
    void successAndReplayAreAtomicAndIssueTicketsOnce() {
        Order order = Order.create("ORD-POSTGRES", USER_ID, EVENT_ID, 300);
        order.addItem(TICKET_TYPE_ID, 2, UNIT_PRICE);
        orderRepository.save(order);
        ticketTypeRepository.save(TicketType.create(
                TICKET_TYPE_ID, EVENT_ID, "Standard", UNIT_PRICE, 10, 5, 300));
        Payment payment = Payment.initiate(
                order.getId(), PaymentProvider.LOCAL, order.getTotalAmount(), TRANSACTION_REF);
        paymentRepository.save(payment);

        PaymentConfirmationTransaction transaction = new PaymentConfirmationTransaction(
                paymentRepository,
                orderRepository,
                ticketTypeRepository,
                ticketRepository,
                outboxEventRepository,
                new UuidTicketCodeGenerator());
        ConfirmPaymentCommand command = new ConfirmPaymentCommand(
                new AuthorizationContext(USER_ID, UserRole.CUSTOMER),
                TRANSACTION_REF,
                PaymentProvider.LOCAL,
                order.getTotalAmount(),
                true);

        PaymentCallbackResult first = transaction.execute(command).callback();
        PaymentCallbackResult replay = transaction.execute(command).callback();

        assertThat(first.outcome()).isEqualTo(PaymentCallbackOutcome.SUCCESS);
        assertThat(replay.outcome()).isEqualTo(PaymentCallbackOutcome.ALREADY_SUCCESS);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findByTransactionRef(TRANSACTION_REF).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
        assertThat(ticketRepository.findAllByOrderItemId(order.getItems().get(0).getId())).hasSize(2);
        assertThat(ticketTypeRepository.findById(TICKET_TYPE_ID).orElseThrow().getSoldQuantity())
                .isEqualTo(2);

        assertThat(outboxEventRepository.findAllPending()).hasSize(1);
        assertThat(outboxEventRepository.findAllPending().get(0).getEventType().name())
                .isEqualTo("PAYMENT_SUCCESS");
    }

    @Test
    void concurrentCallbacksHaveOneWinner() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String transactionRef = "LOCAL-CONCURRENT-" + UUID.randomUUID();
        Order order = Order.create(
                "ORD-CONCURRENT-" + UUID.randomUUID(), userId, eventId, 300);
        order.addItem(ticketTypeId, 1, UNIT_PRICE);
        orderRepository.save(order);
        ticketTypeRepository.save(TicketType.create(
                ticketTypeId, eventId, "Standard", UNIT_PRICE, 10, 5, 300));
        paymentRepository.save(Payment.initiate(
                order.getId(), PaymentProvider.LOCAL, order.getTotalAmount(), transactionRef));

        PaymentConfirmationTransaction transaction = new PaymentConfirmationTransaction(
                paymentRepository,
                orderRepository,
                ticketTypeRepository,
                ticketRepository,
                outboxEventRepository,
                new UuidTicketCodeGenerator());
        ConfirmPaymentCommand command = new ConfirmPaymentCommand(
                new AuthorizationContext(userId, UserRole.CUSTOMER),
                transactionRef,
                PaymentProvider.LOCAL,
                order.getTotalAmount(),
                true);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PaymentCallbackResult> first = executor.submit(() -> {
                start.await();
                return template.execute(status -> transaction.execute(command).callback());
            });
            Future<PaymentCallbackResult> second = executor.submit(() -> {
                start.await();
                return template.execute(status -> transaction.execute(command).callback());
            });
            start.countDown();

            PaymentCallbackResult firstResult = first.get();
            PaymentCallbackResult secondResult = second.get();

            assertThat(List.of(firstResult.outcome(), secondResult.outcome()))
                    .containsExactlyInAnyOrder(PaymentCallbackOutcome.SUCCESS,
                            PaymentCallbackOutcome.ALREADY_SUCCESS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(ticketRepository.findAllByOrderItemId(order.getItems().get(0).getId())).hasSize(1);
        assertThat(ticketTypeRepository.findById(ticketTypeId).orElseThrow().getSoldQuantity())
                .isEqualTo(1);
        int pendingEvents = new TransactionTemplate(transactionManager)
                .execute(status -> outboxEventRepository.findAllPending().size());
        assertThat(pendingEvents).isEqualTo(1);
    }
}

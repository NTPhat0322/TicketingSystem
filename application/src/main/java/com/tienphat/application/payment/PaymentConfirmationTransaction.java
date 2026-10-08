package com.tienphat.application.payment;

import com.tienphat.domain.exception.InvalidPaymentDataException;
import com.tienphat.domain.exception.OrderNotFoundException;
import com.tienphat.domain.exception.PaymentNotFoundException;
import com.tienphat.domain.exception.PaymentOperationRejectedException;
import com.tienphat.domain.exception.InvalidOrderDataException;
import com.tienphat.domain.model.AggregateType;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderItem;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.model.OutboxEventType;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.PaymentStatus;
import com.tienphat.domain.model.Ticket;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.OutboxEventRepository;
import com.tienphat.domain.repository.PaymentRepository;
import com.tienphat.domain.repository.TicketRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Applies a payment callback and all paid-side effects in one PostgreSQL transaction. */
public class PaymentConfirmationTransaction {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TicketCodeGenerator ticketCodeGenerator;

    public PaymentConfirmationTransaction(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            TicketTypeRepository ticketTypeRepository,
            TicketRepository ticketRepository,
            OutboxEventRepository outboxEventRepository,
            TicketCodeGenerator ticketCodeGenerator) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.ticketRepository = ticketRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.ticketCodeGenerator = ticketCodeGenerator;
    }

    @Transactional
    public PaymentConfirmationTransactionResult execute(ConfirmPaymentCommand command) {
        validate(command);

        Payment payment = paymentRepository.findByTransactionRefForUpdate(command.transactionRef())
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment transaction " + command.transactionRef() + " not found"));
        Order order = orderRepository.findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(
                        "Order " + payment.getOrderId() + " not found for payment"));

        command.actor().requireCanAccess(order.getUserId());
        verifyCallbackMatches(payment, command);

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            if (order.getStatus() != OrderStatus.PAID) {
                throw new PaymentOperationRejectedException(
                        "Payment " + payment.getTransactionRef() + " is SUCCESS but its order is "
                                + order.getStatus());
            }
            return new PaymentConfirmationTransactionResult(
                    result(payment, order, PaymentCallbackOutcome.ALREADY_SUCCESS, ticketCount(order)), null);
        }

        if (payment.getStatus() == PaymentStatus.FAILED) {
            if (command.successful()) {
                throw new PaymentOperationRejectedException(
                        "Payment " + payment.getTransactionRef() + " already failed");
            }
            if (order.getStatus() != OrderStatus.FAILED) {
                throw new PaymentOperationRejectedException(
                        "Payment " + payment.getTransactionRef() + " is FAILED but its order is "
                                + order.getStatus());
            }
            return new PaymentConfirmationTransactionResult(
                    result(payment, order, PaymentCallbackOutcome.ALREADY_FAILED, 0),
                    reservationRelease(order));
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new PaymentOperationRejectedException(
                    "Order " + order.getId() + " cannot receive payment from " + order.getStatus());
        }

        if (!command.successful()) {
            payment.markFailed();
            order.fail();
            paymentRepository.save(payment);
            orderRepository.save(order);
            return new PaymentConfirmationTransactionResult(
                    result(payment, order, PaymentCallbackOutcome.FAILED, 0),
                    reservationRelease(order));
        }

        OrderItem item = onlyItem(order);
        TicketType ticketType = ticketTypeRepository.findByIdForUpdate(item.getTicketTypeId())
                .orElseThrow(() -> new PaymentOperationRejectedException(
                        "TicketType " + item.getTicketTypeId() + " not found for payment"));
        if (!order.getEventId().equals(ticketType.getEventId())) {
            throw new PaymentOperationRejectedException(
                    "TicketType " + ticketType.getId() + " does not belong to Order event " + order.getEventId());
        }

        payment.markSuccess();
        order.pay();
        ticketType.confirmSale(item.getQuantity());

        List<Ticket> tickets = new ArrayList<>(item.getQuantity());
        for (int sequence = 1; sequence <= item.getQuantity(); sequence++) {
            tickets.add(Ticket.issueFor(
                    item.getId(),
                    item.getTicketTypeId(),
                    order.getUserId(),
                    ticketCodeGenerator.nextCode(order.getId(), item.getId(), sequence)));
        }

        paymentRepository.save(payment);
        orderRepository.save(order);
        ticketTypeRepository.save(ticketType);
        tickets.forEach(ticketRepository::save);
        outboxEventRepository.save(OutboxEvent.record(
                AggregateType.PAYMENT,
                payment.getId(),
                OutboxEventType.PAYMENT_SUCCESS,
                new PaymentSuccessOutboxPayload(payment, tickets).toJson()));

        return new PaymentConfirmationTransactionResult(
                result(payment, order, PaymentCallbackOutcome.SUCCESS, tickets.size()), null);
    }

    private static void verifyCallbackMatches(Payment payment, ConfirmPaymentCommand command) {
        if (payment.getProvider() != command.provider()
                || !payment.getAmount().equals(command.amount())) {
            throw new PaymentOperationRejectedException(
                    "Payment callback does not match the persisted payment attempt");
        }
    }

    private static PaymentCallbackResult result(
            Payment payment,
            Order order,
            PaymentCallbackOutcome outcome,
            int ticketCount) {
        return new PaymentCallbackResult(
                payment.getId(),
                order.getId(),
                payment.getTransactionRef(),
                payment.getStatus(),
                order.getStatus(),
                outcome,
                ticketCount);
    }

    private static OrderItem onlyItem(Order order) {
        if (order.getItems().size() != 1) {
            throw new InvalidOrderDataException(
                    "Order " + order.getId() + " must contain exactly one item for payment confirmation");
        }
        return order.getItems().get(0);
    }

    private static int ticketCount(Order order) {
        return order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
    }

    private static TicketReservationRelease reservationRelease(Order order) {
        OrderItem item = onlyItem(order);
        return new TicketReservationRelease(
                order.getId(), item.getTicketTypeId(), order.getUserId(), item.getQuantity());
    }

    private static void validate(ConfirmPaymentCommand command) {
        if (command == null) {
            throw new InvalidPaymentDataException("Payment callback command must not be null");
        }
        if (command.actor() == null) {
            throw new InvalidPaymentDataException("Payment callback actor must not be null");
        }
        if (command.transactionRef() == null || command.transactionRef().isBlank()) {
            throw new InvalidPaymentDataException("Payment callback transactionRef must not be blank");
        }
        if (command.provider() == null) {
            throw new InvalidPaymentDataException("Payment callback provider must not be null");
        }
        if (command.amount() == null) {
            throw new InvalidPaymentDataException("Payment callback amount must not be null");
        }
    }
}

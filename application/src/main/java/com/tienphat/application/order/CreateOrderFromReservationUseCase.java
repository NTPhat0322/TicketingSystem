package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.payment.InitiatePaymentCommand;
import com.tienphat.application.payment.InitiatePaymentUseCase;
import com.tienphat.domain.exception.InvalidOrderDataException;
import com.tienphat.domain.model.AggregateType;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderItem;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.model.OutboxEventType;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.OutboxEventRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Creates the durable Order after Redis reservation, with orderId as the idempotency key. */
public class CreateOrderFromReservationUseCase {

    private static final Duration DATABASE_TIMESTAMP_TOLERANCE = Duration.ofNanos(2_000L);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InitiatePaymentUseCase initiatePaymentUseCase;

    public CreateOrderFromReservationUseCase(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            InitiatePaymentUseCase initiatePaymentUseCase) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.initiatePaymentUseCase = initiatePaymentUseCase;
    }

    @Transactional
    public OrderCreationResult execute(CreateOrderFromReservationCommand command) {
        if (command == null) {
            throw new InvalidOrderDataException("Order creation command must not be null");
        }

        Optional<Order> existing = orderRepository.findById(command.orderId());
        if (existing.isPresent()) {
            verifySameReservation(existing.get(), command);
            if (existing.get().getStatus() == OrderStatus.PENDING_PAYMENT) {
                // Repair a duplicate delivery that observed an Order committed before its Payment.
                initiatePayment(existing.get());
            }
            return new OrderCreationResult(command.orderId(), false);
        }

        Order order = Order.create(
                command.orderId(),
                orderCode(command.orderId()),
                command.userId(),
                command.eventId(),
                command.reservedAt(),
                command.expiresAt());
        order.addItem(command.ticketTypeId(), command.quantity(), command.unitPrice());
        Order saved = orderRepository.save(order);
        initiatePayment(saved);

        OutboxEvent event = OutboxEvent.record(
                AggregateType.ORDER,
                saved.getId(),
                OutboxEventType.ORDER_CREATED,
                new OrderCreatedOutboxPayload(command.messageId(), saved).toJson());
        outboxEventRepository.save(event);

        return new OrderCreationResult(saved.getId(), true);
    }

    private void initiatePayment(Order order) {
        initiatePaymentUseCase.execute(new InitiatePaymentCommand(
                new AuthorizationContext(order.getUserId(), UserRole.CUSTOMER),
                order.getId()));
    }

    private static String orderCode(UUID orderId) {
        return "ORD-" + orderId.toString().toUpperCase();
    }

    private static void verifySameReservation(
            Order existing,
            CreateOrderFromReservationCommand command) {
        if (!existing.getUserId().equals(command.userId())
                || !existing.getEventId().equals(command.eventId())
                || !sameTimestamp(existing.getReservedAt(), command.reservedAt())
                || !sameTimestamp(existing.getExpiresAt(), command.expiresAt())
                || existing.getItems().size() != 1) {
            throw new InvalidOrderDataException(
                    "Order " + command.orderId() + " already exists with a different reservation snapshot");
        }

        OrderItem item = existing.getItems().get(0);
        if (!item.getTicketTypeId().equals(command.ticketTypeId())
                || item.getQuantity() != command.quantity()
                || !item.getUnitPrice().equals(command.unitPrice())) {
            throw new InvalidOrderDataException(
                    "Order " + command.orderId() + " already exists with different order items");
        }
    }

    private static boolean sameTimestamp(Instant left, Instant right) {
        return Duration.between(left, right).abs().compareTo(DATABASE_TIMESTAMP_TOLERANCE) <= 0;
    }
}

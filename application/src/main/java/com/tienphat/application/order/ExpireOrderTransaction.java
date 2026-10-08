package com.tienphat.application.order;

import com.tienphat.domain.exception.InvalidOrderDataException;
import com.tienphat.domain.model.AggregateType;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderItem;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.model.OutboxEventType;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.OutboxEventRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Performs only the locked PostgreSQL transition; callers release Redis after this transaction commits. */
public class ExpireOrderTransaction {

    private static final Duration DATABASE_TIMESTAMP_TOLERANCE = Duration.ofNanos(2_000L);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;

    public ExpireOrderTransaction(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository) {
        this(orderRepository, outboxEventRepository, Clock.systemUTC());
    }

    public ExpireOrderTransaction(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.clock = clock;
    }

    @Transactional
    public ExpireOrderResult execute(ExpireOrderCommand command) {
        Order order = orderRepository.findByIdForUpdate(command.orderId()).orElse(null);
        if (order == null) {
            return new ExpireOrderResult(command.orderId(), ExpiryOutcome.ORDER_MISSING, null);
        }

        verifySameReservation(order, command);
        if (clock.instant().isBefore(order.getExpiresAt())) {
            return new ExpireOrderResult(command.orderId(), ExpiryOutcome.NOT_DUE, null);
        }

        return switch (order.getStatus()) {
            case PENDING_PAYMENT -> expire(order, command);
            case EXPIRED -> new ExpireOrderResult(
                    order.getId(), ExpiryOutcome.ALREADY_EXPIRED, releaseReservation(order));
            case PAID -> new ExpireOrderResult(order.getId(), ExpiryOutcome.PAID, null);
            case CANCELLED, FAILED -> new ExpireOrderResult(order.getId(), ExpiryOutcome.OTHER_TERMINAL, null);
        };
    }

    private ExpireOrderResult expire(Order order, ExpireOrderCommand command) {
        order.expire();
        Order saved = orderRepository.save(order);
        outboxEventRepository.save(OutboxEvent.record(
                AggregateType.ORDER,
                saved.getId(),
                OutboxEventType.ORDER_EXPIRED,
                new OrderExpiredOutboxPayload(saved, command).toJson()));
        return new ExpireOrderResult(saved.getId(), ExpiryOutcome.EXPIRED, releaseReservation(saved));
    }

    private static ExpireOrderResult.ReleaseReservation releaseReservation(Order order) {
        if (order.getItems().size() != 1) {
            throw new InvalidOrderDataException(
                    "Order " + order.getId() + " must contain exactly one item for expiry");
        }
        OrderItem item = order.getItems().get(0);
        return new ExpireOrderResult.ReleaseReservation(
                order.getId(), item.getTicketTypeId(), order.getUserId(), item.getQuantity());
    }

    private static void verifySameReservation(Order order, ExpireOrderCommand command) {
        if (!order.getUserId().equals(command.userId())
                || !order.getEventId().equals(command.eventId())
                || !sameTimestamp(order.getReservedAt(), command.reservedAt())
                || !sameTimestamp(order.getExpiresAt(), command.expiresAt())
                || order.getItems().size() != 1) {
            throw new InvalidOrderDataException(
                    "Order " + command.orderId() + " does not match the expiry snapshot");
        }

        OrderItem item = order.getItems().get(0);
        if (!item.getTicketTypeId().equals(command.ticketTypeId())
                || item.getQuantity() != command.quantity()
                || !item.getUnitPrice().equals(command.unitPrice())) {
            throw new InvalidOrderDataException(
                    "Order " + command.orderId() + " has different expiry item data");
        }
    }

    private static boolean sameTimestamp(Instant left, Instant right) {
        return Duration.between(left, right).abs().compareTo(DATABASE_TIMESTAMP_TOLERANCE) <= 0;
    }
}

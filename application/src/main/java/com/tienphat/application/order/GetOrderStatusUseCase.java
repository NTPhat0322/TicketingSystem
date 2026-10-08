package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.exception.OrderNotFoundException;
import com.tienphat.domain.exception.ReservationServiceUnavailableException;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.ReservationIntentStatusPort;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the durable Order first and falls back to the Redis-backed intent while it is CREATING.
 */
@Transactional(readOnly = true)
public class GetOrderStatusUseCase implements UseCase<GetOrderStatusCommand, OrderStatusResult> {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ReservationIntentStatusPort reservationIntentStatusPort;

    public GetOrderStatusUseCase(OrderRepository orderRepository,
                                 PaymentRepository paymentRepository,
                                 ReservationIntentStatusPort reservationIntentStatusPort) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.reservationIntentStatusPort = reservationIntentStatusPort;
    }

    @Override
    public OrderStatusResult execute(GetOrderStatusCommand command) {
        validateCommand(command);
        AuthorizationContext actor = command.actor();
        actor.requireAuthenticated();

        Order order = orderRepository.findById(command.orderId()).orElse(null);
        if (order != null) {
            actor.requireCanAccess(order.getUserId());
            Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
            return OrderStatusResult.fromOrder(order, payment);
        }

        ReservationIntent intent;
        try {
            intent = reservationIntentStatusPort.findByOrderId(command.orderId())
                    .orElseThrow(() -> new OrderNotFoundException(
                            "Order " + command.orderId() + " not found"));
        } catch (OrderNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ReservationServiceUnavailableException(
                    "Reservation status service is unavailable", exception);
        }
        actor.requireCanAccess(intent.userId());

        boolean expired = intent.state() == ReservationIntentState.EXPIRED
                || intent.state() == ReservationIntentState.RELEASED;
        OrderTrackingStatus status = expired
                ? OrderTrackingStatus.EXPIRED
                : OrderTrackingStatus.CREATING;
        return OrderStatusResult.fromIntent(intent, status);
    }

    private static void validateCommand(GetOrderStatusCommand command) {
        if (command == null) {
            throw new InvalidReservationRequestException("Order status command must not be null");
        }
        if (command.orderId() == null) {
            throw new InvalidReservationRequestException("Order status orderId must not be null");
        }
        if (command.actor() == null) {
            throw new InvalidReservationRequestException("Order status actor must not be null");
        }
    }
}

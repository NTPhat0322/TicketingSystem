package com.tienphat.application.order;

import com.tienphat.domain.exception.InvalidOrderDataException;
import com.tienphat.domain.exception.InvalidOrderStateException;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderItem;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Performs only the locked PostgreSQL transition; the caller releases Redis after this transaction commits.
 * An already CANCELLED order is returned unchanged so a retry can re-run the idempotent release.
 */
public class CancelOrderTransaction {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public CancelOrderTransaction(OrderRepository orderRepository, PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Optional<CancelOrderTransactionResult> execute(CancelOrderCommand command) {
        Order order = orderRepository.findByIdForUpdate(command.orderId()).orElse(null);
        if (order == null) {
            return Optional.empty();
        }
        command.actor().requireCanAccess(order.getUserId());

        switch (order.getStatus()) {
            case PENDING_PAYMENT -> {
                order.cancel();
                order = orderRepository.save(order);
            }
            case CANCELLED -> {
                // Retry after a failed post-commit release.
            }
            case PAID, EXPIRED, FAILED -> throw new InvalidOrderStateException(
                    "Order " + order.getId() + " cannot be cancelled from " + order.getStatus());
        }

        // Payment rows are read without a lock: payment confirmation locks payment then order.
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        return Optional.of(new CancelOrderTransactionResult(
                OrderStatusResult.fromOrder(order, payment), releaseReservation(order)));
    }

    private static ExpireOrderResult.ReleaseReservation releaseReservation(Order order) {
        if (order.getItems().size() != 1) {
            throw new InvalidOrderDataException(
                    "Order " + order.getId() + " must contain exactly one item for cancellation");
        }
        OrderItem item = order.getItems().get(0);
        return new ExpireOrderResult.ReleaseReservation(
                order.getId(), item.getTicketTypeId(), order.getUserId(), item.getQuantity());
    }
}

package com.tienphat.application.payment;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.InvalidPaymentDataException;
import com.tienphat.domain.exception.OrderNotFoundException;
import com.tienphat.domain.exception.PaymentOperationRejectedException;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.PaymentStatus;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;

/** Opens one provider attempt from the server-side Order amount snapshot. */
public class InitiatePaymentUseCase
        implements UseCase<InitiatePaymentCommand, PaymentInitiationResult> {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGateway;

    public InitiatePaymentUseCase(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentGatewayPort paymentGateway) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    @Override
    @Transactional
    public PaymentInitiationResult execute(InitiatePaymentCommand command) {
        validate(command);
        command.actor().requireAuthenticated();

        Order order = orderRepository.findByIdForUpdate(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(
                        "Order " + command.orderId() + " not found"));
        command.actor().requireCanAccess(order.getUserId());

        Payment existing = paymentRepository.findByOrderIdForUpdate(order.getId()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == PaymentStatus.FAILED) {
                throw new PaymentOperationRejectedException(
                        "Order " + order.getId() + " already has a failed payment attempt");
            }
            if (existing.getStatus() == PaymentStatus.SUCCESS
                    && order.getStatus() == OrderStatus.PAID) {
                return toResult(existing);
            }
            if (existing.getStatus() == PaymentStatus.PENDING
                    && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                return toResult(existing);
            }
            throw new PaymentOperationRejectedException(
                    "Order " + order.getId() + " cannot reuse payment attempt from "
                            + order.getStatus());
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new PaymentOperationRejectedException(
                    "Order " + order.getId() + " cannot start payment from " + order.getStatus());
        }
        if (order.getTotalAmount().isZero()) {
            throw new PaymentOperationRejectedException(
                    "Order " + order.getId() + " has no payable amount");
        }

        PaymentGatewayPort.PaymentGatewayInitiation initiation = paymentGateway.initiate(
                new PaymentGatewayPort.PaymentGatewayRequest(order.getId(), order.getTotalAmount()));
        if (initiation == null) {
            throw new IllegalStateException("PaymentGatewayPort.initiate must not return null");
        }

        Payment payment = Payment.initiate(
                order.getId(), initiation.provider(), order.getTotalAmount(), initiation.transactionRef());
        Payment saved = paymentRepository.save(payment);
        return toResult(saved);
    }

    private static PaymentInitiationResult toResult(Payment payment) {
        return new PaymentInitiationResult(
                payment.getId(),
                payment.getOrderId(),
                payment.getProvider(),
                payment.getAmount().getAmount(),
                payment.getTransactionRef(),
                payment.getStatus());
    }

    private static void validate(InitiatePaymentCommand command) {
        if (command == null) {
            throw new InvalidPaymentDataException("Payment initiation command must not be null");
        }
        if (command.actor() == null) {
            throw new InvalidPaymentDataException("Payment initiation actor must not be null");
        }
        if (command.orderId() == null) {
            throw new InvalidPaymentDataException("Payment initiation orderId must not be null");
        }
    }

}

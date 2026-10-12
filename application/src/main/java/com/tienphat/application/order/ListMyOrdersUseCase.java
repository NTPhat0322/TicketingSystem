package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.domain.repository.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Persisted orders only: a reservation still CREATING in Redis shows up once the worker writes its row.
 */
@Transactional(readOnly = true)
public class ListMyOrdersUseCase implements UseCase<ListMyOrdersCommand, PageResult<OrderStatusResult>> {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public ListMyOrdersUseCase(OrderRepository orderRepository, PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PageResult<OrderStatusResult> execute(ListMyOrdersCommand command) {
        validateCommand(command);
        AuthorizationContext actor = command.actor();
        actor.requireAuthenticated();

        PageResult<Order> page = orderRepository.findAllByUserId(actor.userId(), command.pageRequest());
        if (page.content().isEmpty()) {
            return new PageResult<>(List.of(), page.page(), page.size(), page.totalElements());
        }

        List<UUID> orderIds = page.content().stream().map(Order::getId).toList();
        Map<UUID, Payment> paymentsByOrderId = paymentRepository.findAllByOrderIds(orderIds).stream()
                .collect(Collectors.toMap(Payment::getOrderId, Function.identity()));
        List<OrderStatusResult> content = page.content().stream()
                .map(order -> OrderStatusResult.fromOrder(order, paymentsByOrderId.get(order.getId())))
                .toList();
        return new PageResult<>(content, page.page(), page.size(), page.totalElements());
    }

    private static void validateCommand(ListMyOrdersCommand command) {
        if (command == null) {
            throw new InvalidReservationRequestException("Order history command must not be null");
        }
        if (command.actor() == null) {
            throw new InvalidReservationRequestException("Order history actor must not be null");
        }
        if (command.pageRequest() == null) {
            throw new InvalidReservationRequestException("Order history pageRequest must not be null");
        }
    }
}

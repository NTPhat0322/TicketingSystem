package com.tienphat.domain.repository;

import com.tienphat.domain.model.Order;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link Order} persistence. Owned by the domain, implemented in {@code infrastructure}.
 *
 * <p>There is deliberately no {@code OrderItemRepository}: {@code OrderItem} is a child entity
 * inside the {@code Order} aggregate and has no independent lifecycle. {@link #save} cascades the
 * lines, and both finders return an order with its lines already attached.
 *
 * <p>{@code orderCode} is the human-facing reference shown to the buyer and sent to the payment
 * gateway; {@code id} is the internal UUIDv7. Both are unique, hence two finders.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByOrderCode(String orderCode);
}

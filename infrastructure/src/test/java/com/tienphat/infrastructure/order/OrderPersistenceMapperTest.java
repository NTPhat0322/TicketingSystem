package com.tienphat.infrastructure.order;

import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderItem;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPersistenceMapperTest {

    private final OrderPersistenceMapper mapper = Mappers.getMapper(OrderPersistenceMapper.class);

    @Test
    void roundTripsEveryOrderAndItemField() {
        Instant reservedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Order order = Order.create(
                Order.generateId(),
                "ORD-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                reservedAt,
                reservedAt.plusSeconds(300));
        order.addItem(UUID.randomUUID(), 2, Money.of(new BigDecimal("125.50")));

        Order roundTripped = mapper.toDomain(mapper.toEntity(order));

        assertThat(roundTripped.getId()).isEqualTo(order.getId());
        assertThat(roundTripped.getOrderCode()).isEqualTo(order.getOrderCode());
        assertThat(roundTripped.getUserId()).isEqualTo(order.getUserId());
        assertThat(roundTripped.getEventId()).isEqualTo(order.getEventId());
        assertThat(roundTripped.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(roundTripped.getTotalAmount()).isEqualTo(order.getTotalAmount());
        assertThat(roundTripped.getReservedAt()).isEqualTo(order.getReservedAt());
        assertThat(roundTripped.getExpiresAt()).isEqualTo(order.getExpiresAt());
        assertThat(roundTripped.getPaidAt()).isNull();
        assertThat(roundTripped.getCreatedAt()).isEqualTo(order.getCreatedAt());
        assertThat(roundTripped.getUpdatedAt()).isEqualTo(order.getUpdatedAt());
        assertThat(roundTripped.getItems()).hasSize(1);

        OrderItem item = roundTripped.getItems().get(0);
        OrderItem expected = order.getItems().get(0);
        assertThat(item.getId()).isEqualTo(expected.getId());
        assertThat(item.getOrderId()).isEqualTo(order.getId());
        assertThat(item.getTicketTypeId()).isEqualTo(expected.getTicketTypeId());
        assertThat(item.getQuantity()).isEqualTo(expected.getQuantity());
        assertThat(item.getUnitPrice()).isEqualTo(expected.getUnitPrice());
        assertThat(item.getSubtotal()).isEqualTo(expected.getSubtotal());
    }

    @Test
    void roundTripsPaidAt() {
        Order order = Order.create(
                Order.generateId(),
                "ORD-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(270));
        order.pay();

        Order roundTripped = mapper.toDomain(mapper.toEntity(order));

        assertThat(roundTripped.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(roundTripped.getPaidAt()).isEqualTo(order.getPaidAt());
    }
}

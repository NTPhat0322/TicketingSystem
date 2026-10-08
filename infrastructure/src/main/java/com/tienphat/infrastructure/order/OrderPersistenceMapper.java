package com.tienphat.infrastructure.order;

import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderItem;
import com.tienphat.domain.vo.Money;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ObjectFactory;

import java.math.BigDecimal;

/** Maps the pure Order aggregate to its infrastructure graph and back. */
@Mapper(componentModel = "spring")
public interface OrderPersistenceMapper {

    OrderJpaEntity toEntity(Order order);

    @Mapping(target = "order", ignore = true)
    OrderItemJpaEntity toEntity(OrderItem item);

    /** The factory reconstructs the child list; MapStruct must not append to its read-only view. */
    @Mapping(target = "items", ignore = true)
    Order toDomain(OrderJpaEntity entity);

    OrderItem toDomain(OrderItemJpaEntity entity);

    default BigDecimal map(Money money) {
        return money == null ? null : money.getAmount();
    }

    default Money map(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }

    /** Reconnects child rows to the owning JPA aggregate after MapStruct maps the list. */
    @AfterMapping
    default void linkItems(@MappingTarget OrderJpaEntity entity) {
        if (entity.getItems() != null) {
            entity.getItems().forEach(item -> item.setOrder(entity));
        }
    }

    @ObjectFactory
    default Order createDomain(OrderJpaEntity entity) {
        return Order.reconstitute(
                entity.getId(),
                entity.getOrderCode(),
                entity.getUserId(),
                entity.getEventId(),
                entity.getStatus(),
                map(entity.getTotalAmount()),
                entity.getItems().stream().map(this::toDomain).toList(),
                entity.getReservedAt(),
                entity.getExpiresAt(),
                entity.getPaidAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    @ObjectFactory
    default OrderItem createDomain(OrderItemJpaEntity entity) {
        return OrderItem.reconstitute(
                entity.getId(),
                entity.getOrder().getId(),
                entity.getTicketTypeId(),
                entity.getQuantity(),
                map(entity.getUnitPrice()),
                map(entity.getSubtotal()));
    }
}

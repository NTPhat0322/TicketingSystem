package com.tienphat.domain.model;

import com.tienphat.domain.exception.InvalidOrderItemException;
import com.tienphat.domain.vo.Money;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

/**
 * One line of an {@link Order}: a quantity of a single {@link TicketType} at the price agreed when
 * the order was placed.
 *
 * <p>A child entity, not an aggregate root. It has its own id (the {@code order_items} row needs a
 * primary key) but no repository and no public factory — {@link #of} is package-private so the only
 * way to create one is {@link Order#addItem}, which keeps {@code totalAmount} in step. See
 * {@link #reconstitute} for why rebuilding from storage is the one exception.
 *
 * <p>{@code unitPrice} is copied from the ticket type at order time rather than looked up later: a
 * tier repriced tomorrow must not change what a buyer owes today. {@code subtotal} is stored
 * alongside it because the schema stores it — the arithmetic is fixed at creation.
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public class OrderItem {

    private final UUID id;
    private final UUID orderId;
    private final UUID ticketTypeId;
    private final int quantity;
    private final Money unitPrice;
    private final Money subtotal;

    private OrderItem(UUID id, UUID orderId, UUID ticketTypeId, int quantity,
                      Money unitPrice, Money subtotal) {
        this.id = id;
        this.orderId = orderId;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    /** Package-private: {@link Order#addItem} is the only legal caller. */
    static OrderItem of(UUID orderId, UUID ticketTypeId, int quantity, Money unitPrice) {
        if (orderId == null) {
            throw new InvalidOrderItemException("OrderItem orderId must not be null");
        }
        if (ticketTypeId == null) {
            throw new InvalidOrderItemException("OrderItem ticketTypeId must not be null");
        }
        if (unitPrice == null) {
            throw new InvalidOrderItemException("OrderItem unitPrice must not be null");
        }
        if (quantity <= 0) {
            throw new InvalidOrderItemException("OrderItem quantity must be positive, but was " + quantity);
        }

        return OrderItem.builder()
                .id(UuidV7Generator.generate())
                .orderId(orderId)
                .ticketTypeId(ticketTypeId)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(unitPrice.multiply(quantity))
                .build();
    }

    /**
     * Rebuilds a stored line. Public — unlike {@link #of}, the persistence mapper in
     * {@code infrastructure} is outside this package and has to be able to call it.
     *
     * <p>That is not a hole in the aggregate boundary: {@code of} generates a fresh id and derives
     * {@code subtotal}, so it cannot reproduce a row, while this takes both as stored and creates
     * nothing new. Null checks only, per the reconstitution convention.
     */
    public static OrderItem reconstitute(UUID id, UUID orderId, UUID ticketTypeId, int quantity,
                                         Money unitPrice, Money subtotal) {
        requireNotNull(id, "id");
        requireNotNull(orderId, "orderId");
        requireNotNull(ticketTypeId, "ticketTypeId");
        requireNotNull(unitPrice, "unitPrice");
        requireNotNull(subtotal, "subtotal");

        return OrderItem.builder()
                .id(id)
                .orderId(orderId)
                .ticketTypeId(ticketTypeId)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .build();
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidOrderItemException("OrderItem " + fieldName + " must not be null");
        }
    }
}

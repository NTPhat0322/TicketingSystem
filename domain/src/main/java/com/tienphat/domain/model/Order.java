package com.tienphat.domain.model;

import com.tienphat.domain.exception.DuplicatePaymentException;
import com.tienphat.domain.exception.InvalidOrderDataException;
import com.tienphat.domain.exception.InvalidOrderStateException;
import com.tienphat.domain.vo.Money;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A purchase in progress: the aggregate root over its {@link OrderItem} lines.
 *
 * <p>The id is a UUIDv7 minted here rather than by the database (design doc §2.1), so an order has
 * an identity before it is ever saved — the outbox row and the payment request can both reference
 * it inside the same transaction.
 *
 * <p>An order does not hold inventory. The hold lives in Redis behind {@code StockCachePort}
 * (design doc §2.4); {@code expiresAt} is a copy of when that hold lapses, kept here so the expiry
 * worker can find stale orders without reading the cache. Nothing in this class touches stock.
 *
 * <p>{@code totalAmount} is derived, never assigned from outside: {@link #addItem} is the only way
 * a line enters the order and it recalculates on every call, so the field cannot drift from
 * {@link #getItems()}.
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public class Order {

    private final UUID id;
    private final String orderCode;
    private final UUID userId;
    private final UUID eventId;
    private OrderStatus status;
    private Money totalAmount;
    private final List<OrderItem> items;
    private final Instant reservedAt;
    private final Instant expiresAt;
    private Instant paidAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Order(UUID id, String orderCode, UUID userId, UUID eventId, OrderStatus status,
                  Money totalAmount, List<OrderItem> items, Instant reservedAt, Instant expiresAt,
                  Instant paidAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderCode = orderCode;
        this.userId = userId;
        this.eventId = eventId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Opens an empty {@code PENDING_PAYMENT} order whose hold lapses {@code holdDurationSec} from
     * now. Lines are added afterwards through {@link #addItem}.
     *
     * <p>{@code holdDurationSec} is passed in rather than read from a {@link TicketType} because an
     * order can in principle span tiers; the caller decides which duration applies.
     */
    public static Order create(String orderCode, UUID userId, UUID eventId, int holdDurationSec) {
        if (orderCode == null || orderCode.isBlank()) {
            throw new InvalidOrderDataException("Order orderCode must not be blank");
        }
        if (userId == null) {
            throw new InvalidOrderDataException("Order userId must not be null");
        }
        if (eventId == null) {
            throw new InvalidOrderDataException("Order eventId must not be null");
        }
        if (holdDurationSec <= 0) {
            throw new InvalidOrderDataException(
                    "Order holdDurationSec must be positive, but was " + holdDurationSec);
        }

        Instant now = Instant.now();
        return Order.builder()
                .id(UuidV7Generator.generate())
                .orderCode(orderCode)
                .userId(userId)
                .eventId(eventId)
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(Money.zero())
                .items(new ArrayList<>())
                .reservedAt(now)
                .expiresAt(now.plusSeconds(holdDurationSec))
                .paidAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Rebuilds an order that already exists in storage, lines included. For persistence mappers
     * only — application code opening a new order calls {@link #create}.
     *
     * <p>{@code create} mints a new UUIDv7, forces {@code PENDING_PAYMENT}, a zero total, no lines
     * and fresh timestamps, so a paid three-line order would come back as an empty pending one.
     *
     * <p>{@code totalAmount} is taken as stored rather than recomputed from {@code items}: the row
     * is the record of what the buyer was charged, and silently correcting it here would hide a
     * persistence bug instead of surfacing it. Null checks only, no business validation.
     */
    public static Order reconstitute(UUID id, String orderCode, UUID userId, UUID eventId,
                                     OrderStatus status, Money totalAmount, List<OrderItem> items,
                                     Instant reservedAt, Instant expiresAt, Instant paidAt,
                                     Instant createdAt, Instant updatedAt) {
        requireNotNull(id, "id");
        requireNotNull(orderCode, "orderCode");
        requireNotNull(userId, "userId");
        requireNotNull(eventId, "eventId");
        requireNotNull(status, "status");
        requireNotNull(totalAmount, "totalAmount");
        requireNotNull(items, "items");
        requireNotNull(reservedAt, "reservedAt");
        requireNotNull(expiresAt, "expiresAt");
        requireNotNull(createdAt, "createdAt");
        requireNotNull(updatedAt, "updatedAt");

        return Order.builder()
                .id(id)
                .orderCode(orderCode)
                .userId(userId)
                .eventId(eventId)
                .status(status)
                .totalAmount(totalAmount)
                .items(new ArrayList<>(items))
                .reservedAt(reservedAt)
                .expiresAt(expiresAt)
                .paidAt(paidAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * Appends a line and recalculates {@code totalAmount}.
     *
     * @throws InvalidOrderStateException if the order has left {@code PENDING_PAYMENT}
     * @throws com.tienphat.domain.exception.InvalidOrderItemException if the line itself is invalid
     */
    public void addItem(UUID ticketTypeId, int quantity, Money unitPrice) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderStateException(
                    "Order " + id + " is " + status + ", items can only be added while PENDING_PAYMENT");
        }

        items.add(OrderItem.of(id, ticketTypeId, quantity, unitPrice));
        recalculateTotal();
        touch();
    }

    /**
     * Settles the order: {@code PENDING_PAYMENT → PAID}, stamping {@code paidAt}.
     *
     * <p>An order that is already {@code PAID} raises {@link DuplicatePaymentException} rather than
     * the generic state error. A replayed gateway callback is routine and the caller can swallow it;
     * paying a cancelled order is a real failure. Two exception types let the caller tell them apart
     * without inspecting {@code status} itself.
     *
     * @throws DuplicatePaymentException  if the order is already {@code PAID}
     * @throws InvalidOrderStateException if the order is in any other terminal status
     */
    public void pay() {
        if (status == OrderStatus.PAID) {
            throw new DuplicatePaymentException("Order " + id + " is already PAID");
        }
        transitionTo(OrderStatus.PAID);
        this.paidAt = Instant.now();
        touch();
    }

    /**
     * The hold lapsed before payment: {@code PENDING_PAYMENT → EXPIRED}.
     *
     * <p>Strict by design. If the order was paid a moment earlier this throws instead of quietly
     * doing nothing, so the expiry worker has to check {@code status == PAID} and skip — design doc
     * §2.2 "nếu đã PAID thì bỏ qua" is a worker decision. A silent no-op here would buy that one
     * caller convenience at the cost of hiding illegal expiry attempts from every other call site.
     *
     * @throws InvalidOrderStateException if the order has already left {@code PENDING_PAYMENT}
     */
    public void expire() {
        transitionTo(OrderStatus.EXPIRED);
        touch();
    }

    /**
     * The buyer abandoned the order: {@code PENDING_PAYMENT → CANCELLED}.
     *
     * @throws InvalidOrderStateException if the order has already left {@code PENDING_PAYMENT}
     */
    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
        touch();
    }

    /**
     * The payment attempt was rejected: {@code PENDING_PAYMENT → FAILED}.
     *
     * <p>Distinct from {@link #cancel}: a failed charge leaves a {@code payments} row behind, an
     * abandoned order does not. Without this method {@code FAILED} would be unreachable state.
     *
     * @throws InvalidOrderStateException if the order has already left {@code PENDING_PAYMENT}
     */
    public void fail() {
        transitionTo(OrderStatus.FAILED);
        touch();
    }

    /** An unmodifiable copy — never the live backing list, which only {@link #addItem} may grow. */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(Money.zero(), Money::add);
    }

    private void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderStateException(
                    "Order " + id + " cannot move from " + status + " to " + target);
        }
        this.status = target;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidOrderDataException("Order " + fieldName + " must not be null");
        }
    }
}

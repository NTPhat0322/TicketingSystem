package com.tienphat.domain.model;

import com.tienphat.domain.exception.InsufficientStockException;
import com.tienphat.domain.exception.InvalidReservationQuantityException;
import com.tienphat.domain.exception.InvalidTicketTypeDataException;
import com.tienphat.domain.exception.TicketTypeNotAvailableException;
import com.tienphat.domain.vo.Money;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * A price tier within an event ("VIP", "Standard"). Its own aggregate root, not a child of
 * {@link Event}.
 *
 * <p>Holding tickets does not happen here. Design doc §2.4 sends every hold through
 * {@code StockCachePort}, so this entity only records sales that have been paid for:
 * {@link #confirmSale} is the single writer that increments {@code soldQuantity} anywhere in the
 * system, and {@link #releaseSale} undoes one after a refund.
 *
 * <p>The mutators maintain, for every non-{@code CLOSED} status:
 * {@code status == SOLD_OUT} ⟺ {@code soldQuantity == totalQuantity}. A {@code CLOSED} type may be
 * at or below capacity. {@code confirmSale}'s guard ordering depends on this invariant.
 * {@link #reconstitute} does not enforce it, so a row written outside the domain can arrive
 * violating it; {@code confirmSale} documents how it copes.
 *
 * <p>{@code version} mirrors the schema's optimistic-lock column. Domain code never changes it —
 * that belongs to the persistence layer — and it guards concurrent organizer edits, not inventory.
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public class TicketType {

    private final UUID id;
    private final UUID eventId;
    private String name;
    private Money price;
    private int totalQuantity;
    private int soldQuantity;
    private int maxPerUser;
    private int holdDurationSec;
    private int version;
    private TicketTypeStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private TicketType(UUID id, UUID eventId, String name, Money price, int totalQuantity,
                       int soldQuantity, int maxPerUser, int holdDurationSec, int version,
                       TicketTypeStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.soldQuantity = soldQuantity;
        this.maxPerUser = maxPerUser;
        this.holdDurationSec = holdDurationSec;
        this.version = version;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Creates an {@code ACTIVE} ticket type with nothing sold. A zero {@code price} is allowed (free tier). */
    public static TicketType create(UUID id, UUID eventId, String name, Money price,
                                    int totalQuantity, int maxPerUser, int holdDurationSec) {
        if (id == null) {
            throw new InvalidTicketTypeDataException("TicketType id must not be null");
        }
        if (eventId == null) {
            throw new InvalidTicketTypeDataException("TicketType eventId must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidTicketTypeDataException("TicketType name must not be blank");
        }
        if (price == null) {
            throw new InvalidTicketTypeDataException("TicketType price must not be null");
        }
        requirePositive(totalQuantity, "totalQuantity");
        requirePositive(maxPerUser, "maxPerUser");
        requirePositive(holdDurationSec, "holdDurationSec");

        Instant now = Instant.now();
        return TicketType.builder()
                .id(id)
                .eventId(eventId)
                .name(name)
                .price(price)
                .totalQuantity(totalQuantity)
                .soldQuantity(0)
                .maxPerUser(maxPerUser)
                .holdDurationSec(holdDurationSec)
                .version(0)
                .status(TicketTypeStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Rebuilds a ticket type that already exists in storage. For persistence mappers only —
     * application code creating a new tier calls {@link #create}.
     *
     * <p>This is the counterpart {@link #create} cannot be: {@code create} forces
     * {@code soldQuantity = 0}, {@code version = 0}, {@code status = ACTIVE} and fresh timestamps,
     * so a tier with 500 tickets sold would come back empty. Here every field arrives as stored.
     *
     * <p>Null checks only, no business validation. The row was valid when written; re-checking it
     * would make a later rule change lock the system out of its own history.
     */
    public static TicketType reconstitute(UUID id, UUID eventId, String name, Money price,
                                          int totalQuantity, int soldQuantity, int maxPerUser,
                                          int holdDurationSec, int version, TicketTypeStatus status,
                                          Instant createdAt, Instant updatedAt) {
        requireNotNull(id, "id");
        requireNotNull(eventId, "eventId");
        requireNotNull(name, "name");
        requireNotNull(price, "price");
        requireNotNull(status, "status");
        requireNotNull(createdAt, "createdAt");
        requireNotNull(updatedAt, "updatedAt");

        return TicketType.builder()
                .id(id)
                .eventId(eventId)
                .name(name)
                .price(price)
                .totalQuantity(totalQuantity)
                .soldQuantity(soldQuantity)
                .maxPerUser(maxPerUser)
                .holdDurationSec(holdDurationSec)
                .version(version)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * Records {@code qty} tickets as paid for. The only method that increments
     * {@code soldQuantity}; intended caller is {@code ConfirmPaymentUseCase} on payment success.
     *
     * <p>The status guard is {@code status == CLOSED}, <strong>not</strong> {@code status != ACTIVE},
     * and the order matters. A {@code SOLD_OUT} type has zero capacity (class invariant), so it is
     * rejected either way — the guard only decides which exception surfaces. Reaching this method
     * on a {@code SOLD_OUT} type means Redis handed out stock Postgres cannot cover; the capacity
     * check reports that as {@link InsufficientStockException}. A {@code != ACTIVE} guard would run
     * first and hide it behind a routine "not available". {@code TicketTypeTest} pins this.
     *
     * <p>{@code soldQuantity} is assigned last, after the transition that can throw. The invariant
     * this method relies on is enforced by the mutators but not by {@link #reconstitute}, so a row
     * whose {@code totalQuantity} was raised outside the domain arrives here as {@code SOLD_OUT}
     * with capacity left. Incrementing first would then leave the counter raised behind a rejected
     * {@code SOLD_OUT → SOLD_OUT} transition — a failed call that still mutated the aggregate. The
     * {@code status != SOLD_OUT} guard skips a transition the row has already made, so such a sale
     * completes and restores {@code soldQuantity == totalQuantity} instead of half-applying.
     *
     * @throws InvalidReservationQuantityException if {@code qty <= 0}
     * @throws TicketTypeNotAvailableException     if the type is {@code CLOSED}
     * @throws InsufficientStockException          if remaining capacity is below {@code qty}
     */
    public void confirmSale(int qty) {
        if (qty <= 0) {
            throw new InvalidReservationQuantityException("Sale quantity must be positive, but was " + qty);
        }
        if (status == TicketTypeStatus.CLOSED) {
            throw new TicketTypeNotAvailableException("TicketType " + id + " is CLOSED");
        }
        int remaining = totalQuantity - soldQuantity;
        if (remaining < qty) {
            throw new InsufficientStockException(
                    "TicketType " + id + " has " + remaining + " remaining, cannot confirm " + qty);
        }

        int newSold = soldQuantity + qty;
        if (newSold == totalQuantity && status != TicketTypeStatus.SOLD_OUT) {
            transitionTo(TicketTypeStatus.SOLD_OUT);
        }
        soldQuantity = newSold;
        touch();
    }

    /**
     * Undoes {@code qty} confirmed sales. Refund/chargeback only — a hold that expires or is
     * cancelled before payment never reached {@link #confirmSale}, so it must go through
     * {@code StockCachePort.release} alone. Calling this for those flows corrupts the counter.
     *
     * <p>Allowed on a {@code CLOSED} type: refunds keep arriving after an organizer stops sales,
     * and after an event is cancelled they are the whole point. The status stays {@code CLOSED}.
     *
     * <p>Assignment comes last for the same reason as {@link #confirmSale}. {@code SOLD_OUT → ACTIVE}
     * is a legal edge, so no stored row makes this throw today; the ordering is what keeps that true
     * if the table ever narrows.
     *
     * @throws InvalidReservationQuantityException if {@code qty <= 0} or it exceeds {@code soldQuantity}
     */
    public void releaseSale(int qty) {
        if (qty <= 0) {
            throw new InvalidReservationQuantityException("Release quantity must be positive, but was " + qty);
        }
        if (qty > soldQuantity) {
            throw new InvalidReservationQuantityException(
                    "TicketType " + id + " has " + soldQuantity + " sold, cannot release " + qty);
        }

        int newSold = soldQuantity - qty;
        if (status == TicketTypeStatus.SOLD_OUT) {
            transitionTo(TicketTypeStatus.ACTIVE);
        }
        soldQuantity = newSold;
        touch();
    }

    /** {@code ACTIVE}/{@code SOLD_OUT → CLOSED}. The organizer stops selling this tier. */
    public void close() {
        transitionTo(TicketTypeStatus.CLOSED);
        touch();
    }

    private void transitionTo(TicketTypeStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new TicketTypeNotAvailableException(
                    "TicketType " + id + " cannot move from " + status + " to " + target);
        }
        this.status = target;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidTicketTypeDataException("TicketType " + fieldName + " must not be null");
        }
    }

    private static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new InvalidTicketTypeDataException(
                    "TicketType " + fieldName + " must be positive, but was " + value);
        }
    }
}

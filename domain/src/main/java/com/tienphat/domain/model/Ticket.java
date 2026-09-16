package com.tienphat.domain.model;

import com.tienphat.domain.exception.InvalidTicketDataException;
import com.tienphat.domain.exception.InvalidTicketStateException;
import com.tienphat.domain.exception.TicketAlreadyCheckedInException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * One admission: the thing a buyer actually shows at the gate.
 *
 * <p>A ticket exists only from payment success onward (design doc §2.2 step 3). Nothing here
 * enforces that — {@link #issueFor} knows nothing about {@code Order} or {@code Payment} on purpose.
 * "On payment success, issue {@code quantity} tickets for each order item" is orchestration and
 * belongs to the future {@code ConfirmPaymentUseCase}; pulling it in here would make this aggregate
 * depend on the whole checkout flow to hand out a single seat.
 *
 * <p>{@code ticketCode} is the QR payload and is unique across the system. It arrives from the
 * caller rather than being generated here: the code is customer-facing and its format is a product
 * decision (length, alphabet, checksum), so it stays outside an entity that only guards the
 * lifecycle.
 *
 * <p>{@code ticketTypeId} is denormalised from the order item (design doc §4) so gate scanning can
 * answer "which tier is this?" without joining back through {@code order_items}.
 *
 * <p>Unlike the other aggregates this has no {@code updatedAt} — the {@code tickets} table has none.
 * Its two timestamps are events, not bookkeeping: {@code issuedAt} and {@code checkedInAt} each get
 * written exactly once and never move.
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public class Ticket {

    private final UUID id;
    private final String ticketCode;
    private final UUID orderItemId;
    private final UUID ticketTypeId;
    private final UUID ownerUserId;
    private TicketStatus status;
    private final Instant issuedAt;
    private Instant checkedInAt;

    private Ticket(UUID id, String ticketCode, UUID orderItemId, UUID ticketTypeId, UUID ownerUserId,
                   TicketStatus status, Instant issuedAt, Instant checkedInAt) {
        this.id = id;
        this.ticketCode = ticketCode;
        this.orderItemId = orderItemId;
        this.ticketTypeId = ticketTypeId;
        this.ownerUserId = ownerUserId;
        this.status = status;
        this.issuedAt = issuedAt;
        this.checkedInAt = checkedInAt;
    }

    /**
     * Issues a fresh {@code ISSUED} ticket. The caller is the payment-confirmation use case, once
     * per unit of {@code OrderItem.quantity}.
     *
     * <p>The id is a plain UUIDv4, not the UUIDv7 used for {@code Order.id}. Orders are written in a
     * burst during a flash sale, where insert-order locality on the primary-key index matters;
     * tickets are written once per sale and read by {@code ticket_code}, so there is nothing to gain
     * and the extra machinery would just be cargo-culted across.
     */
    public static Ticket issueFor(UUID orderItemId, UUID ticketTypeId, UUID ownerUserId, String ticketCode) {
        if (orderItemId == null) {
            throw new InvalidTicketDataException("Ticket orderItemId must not be null");
        }
        if (ticketTypeId == null) {
            throw new InvalidTicketDataException("Ticket ticketTypeId must not be null");
        }
        if (ownerUserId == null) {
            throw new InvalidTicketDataException("Ticket ownerUserId must not be null");
        }
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new InvalidTicketDataException("Ticket ticketCode must not be blank");
        }

        return Ticket.builder()
                .id(UUID.randomUUID())
                .ticketCode(ticketCode)
                .orderItemId(orderItemId)
                .ticketTypeId(ticketTypeId)
                .ownerUserId(ownerUserId)
                .status(TicketStatus.ISSUED)
                .issuedAt(Instant.now())
                .checkedInAt(null)
                .build();
    }

    /**
     * Rebuilds a ticket that already exists in storage. For persistence mappers only — the payment
     * flow issuing a new ticket calls {@link #issueFor}.
     *
     * <p>{@link #issueFor} mints a new id, forces {@code ISSUED}, a fresh {@code issuedAt} and a null
     * {@code checkedInAt}, so a ticket scanned at the gate last night would load as unused — and
     * would then pass {@link #checkIn} again.
     *
     * <p>{@code checkedInAt} is the one nullable column: a ticket that was never admitted has none.
     * Null checks only on the rest, no business validation.
     */
    public static Ticket reconstitute(UUID id, String ticketCode, UUID orderItemId, UUID ticketTypeId,
                                      UUID ownerUserId, TicketStatus status, Instant issuedAt,
                                      Instant checkedInAt) {
        requireNotNull(id, "id");
        requireNotNull(ticketCode, "ticketCode");
        requireNotNull(orderItemId, "orderItemId");
        requireNotNull(ticketTypeId, "ticketTypeId");
        requireNotNull(ownerUserId, "ownerUserId");
        requireNotNull(status, "status");
        requireNotNull(issuedAt, "issuedAt");

        return Ticket.builder()
                .id(id)
                .ticketCode(ticketCode)
                .orderItemId(orderItemId)
                .ticketTypeId(ticketTypeId)
                .ownerUserId(ownerUserId)
                .status(status)
                .issuedAt(issuedAt)
                .checkedInAt(checkedInAt)
                .build();
    }

    /**
     * Admits the holder: {@code ISSUED → CHECKED_IN}, stamping {@code checkedInAt}.
     *
     * <p>A second scan of the same code raises {@link TicketAlreadyCheckedInException}, not the
     * generic state error — the same split as {@code Order.pay()}. The gate needs to tell "this
     * ticket is already inside" (show the staff who and when) apart from "this ticket was refunded"
     * (turn them away), and it should not have to read {@code status} itself to do so.
     *
     * @throws TicketAlreadyCheckedInException if the ticket has already been admitted
     * @throws InvalidTicketStateException     if the ticket is {@code CANCELLED}
     */
    public void checkIn() {
        if (status == TicketStatus.CHECKED_IN) {
            throw new TicketAlreadyCheckedInException(
                    "Ticket " + ticketCode + " was already checked in at " + checkedInAt);
        }
        transitionTo(TicketStatus.CHECKED_IN);
        this.checkedInAt = Instant.now();
    }

    /**
     * Voids the ticket: {@code ISSUED → CANCELLED}, after a refund or a cancelled event.
     *
     * <p>Throws from {@code CHECKED_IN} as well as from {@code CANCELLED}. Someone already through
     * the gate has consumed the admission, so voiding it afterwards is a refund decision for staff,
     * not a state this entity will quietly enter.
     *
     * @throws InvalidTicketStateException if the ticket is not {@code ISSUED}
     */
    public void cancel() {
        transitionTo(TicketStatus.CANCELLED);
    }

    private void transitionTo(TicketStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTicketStateException(
                    "Ticket " + ticketCode + " cannot move from " + status + " to " + target);
        }
        this.status = target;
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidTicketDataException("Ticket " + fieldName + " must not be null");
        }
    }
}

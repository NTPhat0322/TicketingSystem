package com.tienphat.domain.model;

import com.tienphat.domain.exception.DuplicatePaymentException;
import com.tienphat.domain.exception.InvalidPaymentDataException;
import com.tienphat.domain.exception.InvalidPaymentStateException;
import com.tienphat.domain.vo.Money;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to collect money for an {@link Order}, and the record of how it ended.
 *
 * <p>{@code transactionRef} is the gateway's reference and is unique in the schema (design doc §4).
 * That constraint and {@link #markSuccess}'s duplicate guard are two halves of the same defence: the
 * constraint stops the same callback being <em>recorded</em> twice, the guard stops an
 * already-settled attempt being <em>re-settled</em>. Neither replaces the other, which is why
 * {@link DuplicatePaymentException} is shared with {@code Order.pay()} rather than re-declared here.
 *
 * <p>{@code amount} is the amount actually charged, copied from {@code Order.totalAmount} at
 * initiation. It is not re-derived from the order afterwards, for the same reason the order does not
 * re-derive its own total on load: the row is evidence of what the buyer paid, not a projection of
 * what today's pricing rules would say.
 *
 * <p>A zero amount is rejected. A free tier ({@code TicketType} allows a zero price) must not reach
 * a gateway at all — the future {@code CheckoutUseCase} confirms a zero-total order directly instead
 * of opening a payment nobody can settle.
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public class Payment {

    private final UUID id;
    private final UUID orderId;
    private final PaymentProvider provider;
    private final Money amount;
    private PaymentStatus status;
    private final String transactionRef;
    private Instant paidAt;
    private final Instant createdAt;

    private Payment(UUID id, UUID orderId, PaymentProvider provider, Money amount,
                    PaymentStatus status, String transactionRef, Instant paidAt, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.provider = provider;
        this.amount = amount;
        this.status = status;
        this.transactionRef = transactionRef;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
    }

    /**
     * Opens a {@code PENDING} attempt. Called when the buyer is handed off to the gateway; the
     * outcome arrives later through {@link #markSuccess} or {@link #markFailed}.
     *
     * @throws InvalidPaymentDataException if a required field is missing, or {@code amount} is zero
     */
    public static Payment initiate(UUID orderId, PaymentProvider provider, Money amount, String transactionRef) {
        if (orderId == null) {
            throw new InvalidPaymentDataException("Payment orderId must not be null");
        }
        if (provider == null) {
            throw new InvalidPaymentDataException("Payment provider must not be null");
        }
        if (amount == null) {
            throw new InvalidPaymentDataException("Payment amount must not be null");
        }
        if (amount.isZero()) {
            throw new InvalidPaymentDataException("Payment amount must not be zero — a free order needs no payment");
        }
        if (transactionRef == null || transactionRef.isBlank()) {
            throw new InvalidPaymentDataException("Payment transactionRef must not be blank");
        }

        return Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .provider(provider)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .transactionRef(transactionRef)
                .paidAt(null)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Rebuilds a payment that already exists in storage. For persistence mappers only — the checkout
     * flow opening a new attempt calls {@link #initiate}.
     *
     * <p>{@link #initiate} mints a new id, forces {@code PENDING}, a null {@code paidAt} and a fresh
     * {@code createdAt}, so a settled attempt would load as still waiting — and a replayed webhook
     * would then be accepted as the first one.
     *
     * <p>{@code paidAt} is the one nullable column: only a {@code SUCCESS} attempt has one. Null
     * checks only on the rest, and notably no re-check that {@code amount} is non-zero — a row
     * written under different rules must still be readable.
     */
    public static Payment reconstitute(UUID id, UUID orderId, PaymentProvider provider, Money amount,
                                       PaymentStatus status, String transactionRef, Instant paidAt,
                                       Instant createdAt) {
        requireNotNull(id, "id");
        requireNotNull(orderId, "orderId");
        requireNotNull(provider, "provider");
        requireNotNull(amount, "amount");
        requireNotNull(status, "status");
        requireNotNull(transactionRef, "transactionRef");
        requireNotNull(createdAt, "createdAt");

        return Payment.builder()
                .id(id)
                .orderId(orderId)
                .provider(provider)
                .amount(amount)
                .status(status)
                .transactionRef(transactionRef)
                .paidAt(paidAt)
                .createdAt(createdAt)
                .build();
    }

    /**
     * Records a successful charge: {@code PENDING → SUCCESS}, stamping {@code paidAt}.
     *
     * <p>A second call raises {@link DuplicatePaymentException} rather than the generic state error.
     * Gateways retry their callbacks (design doc §2.3), so a replay is expected traffic the handler
     * can answer 200 to; marking a failed attempt successful is a real defect. The exception type is
     * how the caller tells them apart without reading {@code status}.
     *
     * @throws DuplicatePaymentException    if this attempt already succeeded
     * @throws InvalidPaymentStateException if this attempt already failed
     */
    public void markSuccess() {
        if (status == PaymentStatus.SUCCESS) {
            throw new DuplicatePaymentException(
                    "Payment " + transactionRef + " already succeeded at " + paidAt);
        }
        transitionTo(PaymentStatus.SUCCESS);
        this.paidAt = Instant.now();
    }

    /**
     * Records a rejected charge: {@code PENDING → FAILED}.
     *
     * <p>Throws from {@code SUCCESS}: money already collected cannot be un-collected by flipping a
     * status. That situation is a refund, which is a different record, not this one changing its
     * mind.
     *
     * @throws InvalidPaymentStateException if this attempt has already settled
     */
    public void markFailed() {
        transitionTo(PaymentStatus.FAILED);
    }

    private void transitionTo(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidPaymentStateException(
                    "Payment " + transactionRef + " cannot move from " + status + " to " + target);
        }
        this.status = target;
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidPaymentDataException("Payment " + fieldName + " must not be null");
        }
    }
}

package com.tienphat.domain.model;

/**
 * The three outbox events design doc §4 lists.
 *
 * <p>Each constant carries the {@link AggregateType} it can only ever describe. An order does not
 * emit {@code PAYMENT_SUCCESS} and a payment does not emit {@code ORDER_EXPIRED}, so the pairing is
 * a fact about the event, not a field the caller gets to choose. {@link OutboxEvent#record} enforces
 * it — see that method for why the check is worth the extra field.
 *
 * <p>Closed to these three values: a fourth event type is an enum edit, which is the same trade-off
 * {@code PaymentProvider} makes and is recorded in {@code plan.md} Risks.
 */
public enum OutboxEventType {

    ORDER_CREATED(AggregateType.ORDER),
    ORDER_EXPIRED(AggregateType.ORDER),
    PAYMENT_SUCCESS(AggregateType.PAYMENT);

    private final AggregateType ownerType;

    OutboxEventType(AggregateType ownerType) {
        this.ownerType = ownerType;
    }

    /** The only aggregate type this event can be recorded against. */
    public AggregateType ownerType() {
        return ownerType;
    }
}

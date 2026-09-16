package com.tienphat.domain.model;

import com.tienphat.domain.exception.InvalidOutboxEventDataException;
import com.tienphat.domain.exception.InvalidOutboxEventStateException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * One message waiting to leave the system, stored as a row instead of being published directly.
 *
 * <p>Infrastructure in purpose, domain-modeled in form (design doc §3). The point of the pattern
 * (§2.5) is that the row is written <em>in the same transaction</em> as the business change that
 * caused it: if that transaction commits, the event exists; if it rolls back, the event never
 * existed. Publishing inside the transaction instead is the dual-write this avoids, where a crash
 * between the commit and the broker loses the message with no trace.
 *
 * <p>{@code payload} is an opaque JSON {@code String}. The domain has no JSON library and should not
 * gain one to store a blob it never reads — serialisation belongs to the infrastructure publisher.
 * The consequence is that nothing here validates the payload's shape; a malformed body is caught by
 * the consumer, not by this class.
 *
 * <p>There is no attempt counter. {@code outbox_events} (design doc §4) has no {@code retry_count}
 * column, so an event that fails permanently can cycle {@code FAILED → PENDING → FAILED} forever as
 * far as the domain is concerned. Bounding that is the poller's job — a max-attempts cap or a dead
 * letter table — and it needs a schema column before this class can help.
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public class OutboxEvent {

    private final UUID id;
    private final AggregateType aggregateType;
    private final UUID aggregateId;
    private final OutboxEventType eventType;
    private final String payload;
    private OutboxEventStatus status;
    private final Instant createdAt;
    private Instant publishedAt;

    private OutboxEvent(UUID id, AggregateType aggregateType, UUID aggregateId, OutboxEventType eventType,
                        String payload, OutboxEventStatus status, Instant createdAt, Instant publishedAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    /**
     * Records a new event as {@code PENDING}, for the poller to pick up after the surrounding
     * transaction commits.
     *
     * <p>Rejects an {@code eventType} whose {@link OutboxEventType#ownerType()} does not match
     * {@code aggregateType} — for example {@code PAYMENT_SUCCESS} filed under {@code ORDER}. The
     * columns are independent in the schema, so nothing downstream would notice the mismatch; the
     * publisher would route the message by one field and the consumer would look up the wrong
     * aggregate by the other. Catching it here costs one comparison at write time.
     *
     * @throws InvalidOutboxEventDataException if a required field is missing, {@code payload} is
     *                                         blank, or the event type does not belong to the
     *                                         aggregate type
     */
    public static OutboxEvent record(AggregateType aggregateType, UUID aggregateId,
                                     OutboxEventType eventType, String payload) {
        if (aggregateType == null) {
            throw new InvalidOutboxEventDataException("OutboxEvent aggregateType must not be null");
        }
        if (aggregateId == null) {
            throw new InvalidOutboxEventDataException("OutboxEvent aggregateId must not be null");
        }
        if (eventType == null) {
            throw new InvalidOutboxEventDataException("OutboxEvent eventType must not be null");
        }
        if (eventType.ownerType() != aggregateType) {
            throw new InvalidOutboxEventDataException(
                    "OutboxEvent eventType " + eventType + " belongs to " + eventType.ownerType()
                            + ", not " + aggregateType);
        }
        if (payload == null || payload.isBlank()) {
            throw new InvalidOutboxEventDataException("OutboxEvent payload must not be blank");
        }

        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .createdAt(Instant.now())
                .publishedAt(null)
                .build();
    }

    /**
     * Rebuilds a row the poller has just read. For persistence mappers only — a new event uses
     * {@link #record}.
     *
     * <p>This one matters more than the other aggregates' reconstitution. {@link #record} mints a
     * fresh id and forces {@code PENDING}, so a mapper that used it would hand the poller a new row
     * every poll: already-published events would look unsent and be delivered again, and
     * {@code save} would insert duplicates instead of updating the row it read.
     *
     * <p>{@code publishedAt} is the one nullable column. No re-validation of the payload or the
     * type/aggregate pairing — a row written under older rules must still be loadable, if only so
     * an operator can see it and decide what to do with it.
     */
    public static OutboxEvent reconstitute(UUID id, AggregateType aggregateType, UUID aggregateId,
                                           OutboxEventType eventType, String payload,
                                           OutboxEventStatus status, Instant createdAt, Instant publishedAt) {
        requireNotNull(id, "id");
        requireNotNull(aggregateType, "aggregateType");
        requireNotNull(aggregateId, "aggregateId");
        requireNotNull(eventType, "eventType");
        requireNotNull(payload, "payload");
        requireNotNull(status, "status");
        requireNotNull(createdAt, "createdAt");

        return OutboxEvent.builder()
                .id(id)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .status(status)
                .createdAt(createdAt)
                .publishedAt(publishedAt)
                .build();
    }

    /**
     * Marks the row delivered: {@code PENDING → PUBLISHED}, stamping {@code publishedAt}.
     *
     * @throws InvalidOutboxEventStateException if the row was already published, or failed and has
     *                                          not been retried back to {@code PENDING}
     */
    public void markPublished() {
        transitionTo(OutboxEventStatus.PUBLISHED);
        this.publishedAt = Instant.now();
    }

    /**
     * Marks a delivery attempt rejected: {@code PENDING → FAILED}. Not terminal — {@link #retry}
     * puts the row back in the queue.
     *
     * @throws InvalidOutboxEventStateException if the row is not currently {@code PENDING}
     */
    public void markFailed() {
        transitionTo(OutboxEventStatus.FAILED);
    }

    /**
     * Puts a failed row back in the queue: {@code FAILED → PENDING}.
     *
     * <p>Only from {@code FAILED}. Retrying a {@code PENDING} row is a poller bug — the row is
     * already queued, and resetting it would do nothing while suggesting it did something. Retrying
     * a {@code PUBLISHED} row would re-send a message that already left.
     *
     * @throws InvalidOutboxEventStateException if the row has not failed
     */
    public void retry() {
        transitionTo(OutboxEventStatus.PENDING);
    }

    private void transitionTo(OutboxEventStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOutboxEventStateException(
                    "OutboxEvent " + id + " cannot move from " + status + " to " + target);
        }
        this.status = target;
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidOutboxEventDataException("OutboxEvent " + fieldName + " must not be null");
        }
    }
}

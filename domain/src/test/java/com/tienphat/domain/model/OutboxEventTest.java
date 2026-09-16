package com.tienphat.domain.model;

import com.tienphat.domain.exception.InvalidOutboxEventDataException;
import com.tienphat.domain.exception.InvalidOutboxEventStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventTest {

    private static final UUID AGGREGATE_ID = UUID.randomUUID();
    private static final String PAYLOAD = "{\"orderId\":\"9f1\",\"total\":300000}";

    private static OutboxEvent aPendingEvent() {
        return OutboxEvent.record(AggregateType.ORDER, AGGREGATE_ID, OutboxEventType.ORDER_CREATED, PAYLOAD);
    }

    private static OutboxEvent anEventIn(OutboxEventStatus status) {
        OutboxEvent event = aPendingEvent();
        switch (status) {
            case PENDING -> { }
            case PUBLISHED -> event.markPublished();
            case FAILED -> event.markFailed();
        }
        return event;
    }

    @Test
    @DisplayName("record() stores a PENDING row that has not been delivered yet")
    void record_succeeds() {
        OutboxEvent event = aPendingEvent();

        assertThat(event.getId()).isNotNull();
        assertThat(event.getAggregateType()).isEqualTo(AggregateType.ORDER);
        assertThat(event.getAggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(event.getEventType()).isEqualTo(OutboxEventType.ORDER_CREATED);
        assertThat(event.getPayload()).isEqualTo(PAYLOAD);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getPublishedAt()).as("nothing delivered yet").isNull();
    }

    @Test
    @DisplayName("record() rejects a blank payload — an empty message is worse than no message")
    void record_rejectsBlankPayload() {
        assertThatThrownBy(() -> OutboxEvent.record(AggregateType.ORDER, AGGREGATE_ID,
                OutboxEventType.ORDER_CREATED, "   "))
                .isInstanceOf(InvalidOutboxEventDataException.class)
                .hasMessageContaining("payload");

        assertThatThrownBy(() -> OutboxEvent.record(AggregateType.ORDER, AGGREGATE_ID,
                OutboxEventType.ORDER_CREATED, null))
                .isInstanceOf(InvalidOutboxEventDataException.class)
                .hasMessageContaining("payload");
    }

    @Test
    @DisplayName("record() rejects a null aggregateType, aggregateId or eventType")
    void record_rejectsNullRequiredFields() {
        assertThatThrownBy(() -> OutboxEvent.record(null, AGGREGATE_ID, OutboxEventType.ORDER_CREATED, PAYLOAD))
                .isInstanceOf(InvalidOutboxEventDataException.class)
                .hasMessageContaining("aggregateType");

        assertThatThrownBy(() -> OutboxEvent.record(AggregateType.ORDER, null, OutboxEventType.ORDER_CREATED, PAYLOAD))
                .isInstanceOf(InvalidOutboxEventDataException.class)
                .hasMessageContaining("aggregateId");

        assertThatThrownBy(() -> OutboxEvent.record(AggregateType.ORDER, AGGREGATE_ID, null, PAYLOAD))
                .isInstanceOf(InvalidOutboxEventDataException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    @DisplayName("record() rejects an eventType filed under the wrong aggregateType")
    void record_rejectsMismatchedAggregateType() {
        assertThatThrownBy(() -> OutboxEvent.record(AggregateType.ORDER, AGGREGATE_ID,
                OutboxEventType.PAYMENT_SUCCESS, PAYLOAD))
                .isInstanceOf(InvalidOutboxEventDataException.class)
                .hasMessageContaining("PAYMENT_SUCCESS");

        assertThatThrownBy(() -> OutboxEvent.record(AggregateType.PAYMENT, AGGREGATE_ID,
                OutboxEventType.ORDER_EXPIRED, PAYLOAD))
                .isInstanceOf(InvalidOutboxEventDataException.class)
                .hasMessageContaining("ORDER_EXPIRED");
    }

    @Test
    @DisplayName("every OutboxEventType can be recorded against its own aggregate type")
    void record_acceptsEveryEventTypeWithItsOwner() {
        for (OutboxEventType type : OutboxEventType.values()) {
            OutboxEvent event = OutboxEvent.record(type.ownerType(), AGGREGATE_ID, type, PAYLOAD);

            assertThat(event.getEventType()).as("event type %s", type).isEqualTo(type);
            assertThat(event.getAggregateType()).isEqualTo(type.ownerType());
        }
    }

    @Test
    @DisplayName("every AggregateType owns at least one event type — no unusable constant")
    void everyAggregateType_hasAnEventType() {
        Set<AggregateType> owned = EnumSet.noneOf(AggregateType.class);

        for (OutboxEventType type : OutboxEventType.values()) {
            owned.add(type.ownerType());
        }

        assertThat(owned).containsExactlyInAnyOrder(AggregateType.values());
    }

    @Test
    @DisplayName("markPublished() delivers a PENDING row and stamps publishedAt")
    void markPublished_succeedsFromPending() {
        OutboxEvent event = aPendingEvent();

        event.markPublished();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getPublishedAt()).isAfterOrEqualTo(event.getCreatedAt());
    }

    @Test
    @DisplayName("markPublished() twice throws — the poller lost track of its own work")
    void markPublished_twiceThrows() {
        OutboxEvent event = anEventIn(OutboxEventStatus.PUBLISHED);
        Instant firstPublishedAt = event.getPublishedAt();

        assertThatThrownBy(event::markPublished)
                .isInstanceOf(InvalidOutboxEventStateException.class);

        assertThat(event.getPublishedAt()).as("the first delivery timestamp stands").isEqualTo(firstPublishedAt);
    }

    @Test
    @DisplayName("markFailed() records a rejected attempt from PENDING without stamping publishedAt")
    void markFailed_succeedsFromPending() {
        OutboxEvent event = aPendingEvent();

        event.markFailed();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("markFailed() on a PUBLISHED row throws — a delivered message cannot un-deliver")
    void markFailed_onPublishedThrows() {
        OutboxEvent event = anEventIn(OutboxEventStatus.PUBLISHED);
        Instant publishedAt = event.getPublishedAt();

        assertThatThrownBy(event::markFailed)
                .isInstanceOf(InvalidOutboxEventStateException.class);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    @DisplayName("retry() puts a FAILED row back in the queue")
    void retry_succeedsFromFailed() {
        OutboxEvent event = anEventIn(OutboxEventStatus.FAILED);

        event.retry();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("a retried row can be published, keeping its original id and createdAt")
    void retry_thenPublish_completesTheLoop() {
        OutboxEvent event = anEventIn(OutboxEventStatus.FAILED);
        UUID id = event.getId();
        Instant createdAt = event.getCreatedAt();

        event.retry();
        event.markPublished();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getId()).as("a retry is the same row, not a new one").isEqualTo(id);
        assertThat(event.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("retry() on a PENDING row throws — it is already queued")
    void retry_onPendingThrows() {
        OutboxEvent event = aPendingEvent();

        assertThatThrownBy(event::retry)
                .isInstanceOf(InvalidOutboxEventStateException.class);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("retry() on a PUBLISHED row throws — it would re-send a message that already left")
    void retry_onPublishedThrows() {
        OutboxEvent event = anEventIn(OutboxEventStatus.PUBLISHED);

        assertThatThrownBy(event::retry)
                .isInstanceOf(InvalidOutboxEventStateException.class);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("PUBLISHED rejects every mutator; FAILED accepts only retry()")
    void terminalAndFailedStatuses_guardTheirMutators() {
        OutboxEvent published = anEventIn(OutboxEventStatus.PUBLISHED);
        assertThatThrownBy(published::markPublished).isInstanceOf(InvalidOutboxEventStateException.class);
        assertThatThrownBy(published::markFailed).isInstanceOf(InvalidOutboxEventStateException.class);
        assertThatThrownBy(published::retry).isInstanceOf(InvalidOutboxEventStateException.class);
        assertThat(published.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);

        OutboxEvent failed = anEventIn(OutboxEventStatus.FAILED);
        assertThatThrownBy(failed::markPublished).isInstanceOf(InvalidOutboxEventStateException.class);
        assertThatThrownBy(failed::markFailed).isInstanceOf(InvalidOutboxEventStateException.class);
        assertThat(failed.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    @Test
    @DisplayName("every OutboxEventStatus is reachable through a public mutator — no dead status")
    void everyStatus_isReachable() {
        Set<OutboxEventStatus> reached = EnumSet.noneOf(OutboxEventStatus.class);

        for (OutboxEventStatus status : OutboxEventStatus.values()) {
            reached.add(anEventIn(status).getStatus());
        }

        assertThat(reached).containsExactlyInAnyOrder(OutboxEventStatus.values());
    }

    @Test
    @DisplayName("reconstitute() restores a published row so the poller does not re-send it")
    void reconstitute_preservesStoredState() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T09:00:00Z");
        Instant publishedAt = Instant.parse("2026-06-01T09:00:03Z");

        OutboxEvent event = OutboxEvent.reconstitute(id, AggregateType.PAYMENT, AGGREGATE_ID,
                OutboxEventType.PAYMENT_SUCCESS, PAYLOAD, OutboxEventStatus.PUBLISHED, createdAt, publishedAt);

        assertThat(event.getId()).as("record() would mint a new id").isEqualTo(id);
        assertThat(event.getStatus()).as("record() would force PENDING").isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).as("record() would force null").isEqualTo(publishedAt);
        assertThat(event.getCreatedAt()).as("record() would force now()").isEqualTo(createdAt);
        assertThatThrownBy(event::markPublished)
                .as("a delivered row stays delivered across a restart")
                .isInstanceOf(InvalidOutboxEventStateException.class);
    }

    @Test
    @DisplayName("reconstitute() accepts a null publishedAt but rejects a null required column")
    void reconstitute_nullRules() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        OutboxEvent pending = OutboxEvent.reconstitute(id, AggregateType.ORDER, AGGREGATE_ID,
                OutboxEventType.ORDER_EXPIRED, PAYLOAD, OutboxEventStatus.PENDING, createdAt, null);
        assertThat(pending.getPublishedAt()).as("publishedAt is null until delivery").isNull();

        assertThatThrownBy(() -> OutboxEvent.reconstitute(id, AggregateType.ORDER, AGGREGATE_ID,
                OutboxEventType.ORDER_EXPIRED, PAYLOAD, null, createdAt, null))
                .isInstanceOf(InvalidOutboxEventDataException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("reconstitute() does not re-apply the payload and pairing rules record() enforces")
    void reconstitute_doesNotRevalidateBusinessRules() {
        OutboxEvent legacy = OutboxEvent.reconstitute(UUID.randomUUID(), AggregateType.ORDER, AGGREGATE_ID,
                OutboxEventType.PAYMENT_SUCCESS, "", OutboxEventStatus.FAILED, Instant.now(), null);

        assertThat(legacy.getPayload()).as("a row written under older rules stays readable").isEmpty();
        assertThat(legacy.getEventType()).isEqualTo(OutboxEventType.PAYMENT_SUCCESS);
        assertThat(legacy.getAggregateType()).isEqualTo(AggregateType.ORDER);
    }

    @Test
    @DisplayName("equality is by id alone")
    void equality_isIdentityBased() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        OutboxEvent one = OutboxEvent.reconstitute(id, AggregateType.ORDER, AGGREGATE_ID,
                OutboxEventType.ORDER_CREATED, PAYLOAD, OutboxEventStatus.PENDING, now, null);
        OutboxEvent sameIdDifferentFields = OutboxEvent.reconstitute(id, AggregateType.PAYMENT, UUID.randomUUID(),
                OutboxEventType.PAYMENT_SUCCESS, "{}", OutboxEventStatus.FAILED, now, null);

        assertThat(one).isEqualTo(sameIdDifferentFields);
        assertThat(one).hasSameHashCodeAs(sameIdDifferentFields);
        assertThat(one).isNotEqualTo(aPendingEvent());
    }
}

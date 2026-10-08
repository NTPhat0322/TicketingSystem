package com.tienphat.infrastructure.outbox;

import com.tienphat.domain.model.AggregateType;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.model.OutboxEventStatus;
import com.tienphat.domain.model.OutboxEventType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventPersistenceMapperTest {

    private final OutboxEventPersistenceMapper mapper = Mappers.getMapper(OutboxEventPersistenceMapper.class);

    @Test
    void roundTripsEveryFieldIncludingPublishedAt() {
        OutboxEvent event = OutboxEvent.record(
                AggregateType.ORDER,
                UUID.randomUUID(),
                OutboxEventType.ORDER_CREATED,
                "{\"orderId\":\"123\"}");
        event.markPublished();

        OutboxEvent roundTripped = mapper.toDomain(mapper.toEntity(event));

        assertThat(roundTripped.getId()).isEqualTo(event.getId());
        assertThat(roundTripped.getAggregateType()).isEqualTo(AggregateType.ORDER);
        assertThat(roundTripped.getAggregateId()).isEqualTo(event.getAggregateId());
        assertThat(roundTripped.getEventType()).isEqualTo(OutboxEventType.ORDER_CREATED);
        assertThat(roundTripped.getPayload()).isEqualTo(event.getPayload());
        assertThat(roundTripped.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(roundTripped.getCreatedAt()).isEqualTo(event.getCreatedAt());
        assertThat(roundTripped.getPublishedAt()).isEqualTo(event.getPublishedAt());
    }
}

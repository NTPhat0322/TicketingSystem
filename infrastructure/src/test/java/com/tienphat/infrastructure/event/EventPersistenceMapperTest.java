package com.tienphat.infrastructure.event;

import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventPersistenceMapperTest {

    private final EventPersistenceMapper mapper = Mappers.getMapper(EventPersistenceMapper.class);

    @Test
    void roundTripsEveryFieldThroughEntityAndBackToDomain() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Event event = Event.reconstitute(
                UUID.randomUUID(), UUID.randomUUID(), "Concert", "A concert", "My Dinh Stadium",
                now.plus(3, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS),
                EventStatus.PUBLISHED, now, now.plus(1, ChronoUnit.HOURS));

        EventJpaEntity entity = mapper.toEntity(event);
        Event roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(event.getId());
        assertThat(roundTripped.getOrganizerId()).isEqualTo(event.getOrganizerId());
        assertThat(roundTripped.getName()).isEqualTo(event.getName());
        assertThat(roundTripped.getDescription()).isEqualTo(event.getDescription());
        assertThat(roundTripped.getVenueName()).isEqualTo(event.getVenueName());
        assertThat(roundTripped.getStartTime()).isEqualTo(event.getStartTime());
        assertThat(roundTripped.getEndTime()).isEqualTo(event.getEndTime());
        assertThat(roundTripped.getSaleStartTime()).isEqualTo(event.getSaleStartTime());
        assertThat(roundTripped.getSaleEndTime()).isEqualTo(event.getSaleEndTime());
        assertThat(roundTripped.getStatus()).isEqualTo(event.getStatus());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(event.getCreatedAt());
        assertThat(roundTripped.getUpdatedAt()).isEqualTo(event.getUpdatedAt());
    }

    @Test
    void preservesNullDescription() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Event event = Event.reconstitute(
                UUID.randomUUID(), UUID.randomUUID(), "Concert", null, "My Dinh Stadium",
                now.plus(3, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS),
                EventStatus.DRAFT, now, now);

        Event roundTripped = mapper.toDomain(mapper.toEntity(event));

        assertThat(roundTripped.getDescription()).isNull();
    }
}

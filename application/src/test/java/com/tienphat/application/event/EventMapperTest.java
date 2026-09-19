package com.tienphat.application.event;

import com.tienphat.domain.model.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventMapperTest {

    private final EventMapper eventMapper = Mappers.getMapper(EventMapper.class);

    @Test
    @DisplayName("toResult() copies every field from the generated mapper implementation")
    void toResult_copiesEveryField() {
        UUID id = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        Instant saleStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEnd = saleStart.plus(7, ChronoUnit.DAYS);
        Instant start = saleEnd.plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);
        Event event = Event.create(id, organizerId, "Concert", "A concert", "My Dinh Stadium",
                start, end, saleStart, saleEnd);

        EventResult result = eventMapper.toResult(event);

        assertThat(result.id()).isEqualTo(event.getId());
        assertThat(result.organizerId()).isEqualTo(event.getOrganizerId());
        assertThat(result.name()).isEqualTo(event.getName());
        assertThat(result.description()).isEqualTo(event.getDescription());
        assertThat(result.venueName()).isEqualTo(event.getVenueName());
        assertThat(result.startTime()).isEqualTo(event.getStartTime());
        assertThat(result.endTime()).isEqualTo(event.getEndTime());
        assertThat(result.saleStartTime()).isEqualTo(event.getSaleStartTime());
        assertThat(result.saleEndTime()).isEqualTo(event.getSaleEndTime());
        assertThat(result.status()).isEqualTo(event.getStatus());
        assertThat(result.createdAt()).isEqualTo(event.getCreatedAt());
        assertThat(result.updatedAt()).isEqualTo(event.getUpdatedAt());
    }
}

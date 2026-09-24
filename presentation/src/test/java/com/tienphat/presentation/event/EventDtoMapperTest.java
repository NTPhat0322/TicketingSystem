package com.tienphat.presentation.event;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.event.CreateEventCommand;
import com.tienphat.application.event.EventResult;
import com.tienphat.application.event.UpdateEventCommand;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.presentation.dto.PageResponse;
import com.tienphat.presentation.event.dto.CreateEventRequest;
import com.tienphat.presentation.event.dto.EventResponse;
import com.tienphat.presentation.event.dto.UpdateEventRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventDtoMapperTest {

    private final EventDtoMapper mapper = Mappers.getMapper(EventDtoMapper.class);

    @Test
    @DisplayName("toCommand(CreateEventRequest, actor) preserves every field and actor")
    void toCommand_fromCreateRequest_preservesEveryField() {
        Instant saleStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEnd = saleStart.plus(7, ChronoUnit.DAYS);
        Instant start = saleEnd.plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);
        CreateEventRequest request = new CreateEventRequest(
                "Concert", "A concert", "My Dinh Stadium", start, end, saleStart, saleEnd);
        AuthorizationContext actor = new AuthorizationContext(UUID.randomUUID(), UserRole.ORGANIZER);

        CreateEventCommand command = mapper.toCommand(request, actor);

        assertThat(command.actor()).isEqualTo(actor);
        assertThat(command.name()).isEqualTo("Concert");
        assertThat(command.description()).isEqualTo("A concert");
        assertThat(command.venueName()).isEqualTo("My Dinh Stadium");
        assertThat(command.startTime()).isEqualTo(start);
        assertThat(command.endTime()).isEqualTo(end);
        assertThat(command.saleStartTime()).isEqualTo(saleStart);
        assertThat(command.saleEndTime()).isEqualTo(saleEnd);
    }

    @Test
    @DisplayName("toCommand(id, UpdateEventRequest, actor) preserves the path id plus every body field")
    void toCommand_fromUpdateRequest_preservesPathIdAndEveryField() {
        Instant saleStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEnd = saleStart.plus(7, ChronoUnit.DAYS);
        Instant start = saleEnd.plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);
        UUID id = UUID.randomUUID();
        UpdateEventRequest request = new UpdateEventRequest(
                "Concert v2", "Updated description", "New Venue", start, end, saleStart, saleEnd);

        AuthorizationContext actor = new AuthorizationContext(UUID.randomUUID(), UserRole.ORGANIZER);
        UpdateEventCommand command = mapper.toCommand(id, request, actor);

        assertThat(command.id()).isEqualTo(id);
        assertThat(command.actor()).isEqualTo(actor);
        assertThat(command.name()).isEqualTo("Concert v2");
        assertThat(command.description()).isEqualTo("Updated description");
        assertThat(command.venueName()).isEqualTo("New Venue");
        assertThat(command.startTime()).isEqualTo(start);
        assertThat(command.endTime()).isEqualTo(end);
        assertThat(command.saleStartTime()).isEqualTo(saleStart);
        assertThat(command.saleEndTime()).isEqualTo(saleEnd);
    }

    @Test
    @DisplayName("toResponse() preserves every field including status/createdAt/updatedAt")
    void toResponse_preservesEveryField() {
        Instant now = Instant.now();
        EventResult result = new EventResult(
                UUID.randomUUID(), UUID.randomUUID(), "Concert", "A concert", "My Dinh Stadium",
                now.plus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                EventStatus.DRAFT, now, now);

        EventResponse response = mapper.toResponse(result);

        assertThat(response.id()).isEqualTo(result.id());
        assertThat(response.organizerId()).isEqualTo(result.organizerId());
        assertThat(response.name()).isEqualTo(result.name());
        assertThat(response.description()).isEqualTo(result.description());
        assertThat(response.venueName()).isEqualTo(result.venueName());
        assertThat(response.startTime()).isEqualTo(result.startTime());
        assertThat(response.endTime()).isEqualTo(result.endTime());
        assertThat(response.saleStartTime()).isEqualTo(result.saleStartTime());
        assertThat(response.saleEndTime()).isEqualTo(result.saleEndTime());
        assertThat(response.status()).isEqualTo(result.status());
        assertThat(response.createdAt()).isEqualTo(result.createdAt());
        assertThat(response.updatedAt()).isEqualTo(result.updatedAt());
    }

    @Test
    @DisplayName("toPageResponse() maps content and preserves page/size/totalElements/totalPages")
    void toPageResponse_mapsContentAndPreservesPagingFields() {
        Instant now = Instant.now();
        EventResult resultA = new EventResult(UUID.randomUUID(), UUID.randomUUID(), "A", "descA", "VenueA",
                now, now, now, now, EventStatus.DRAFT, now, now);
        EventResult resultB = new EventResult(UUID.randomUUID(), UUID.randomUUID(), "B", "descB", "VenueB",
                now, now, now, now, EventStatus.PUBLISHED, now, now);
        PageResult<EventResult> pageResult = new PageResult<>(List.of(resultA, resultB), 0, 20, 2);

        PageResponse<EventResponse> pageResponse = mapper.toPageResponse(pageResult);

        assertThat(pageResponse.content()).hasSize(2);
        assertThat(pageResponse.content().get(0).id()).isEqualTo(resultA.id());
        assertThat(pageResponse.content().get(1).id()).isEqualTo(resultB.id());
        assertThat(pageResponse.page()).isEqualTo(0);
        assertThat(pageResponse.size()).isEqualTo(20);
        assertThat(pageResponse.totalElements()).isEqualTo(2);
        assertThat(pageResponse.totalPages()).isEqualTo(1);
    }
}

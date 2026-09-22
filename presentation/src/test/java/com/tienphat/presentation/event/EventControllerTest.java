package com.tienphat.presentation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienphat.application.event.CreateEventCommand;
import com.tienphat.application.event.DeactivateEventCommand;
import com.tienphat.application.event.EventResult;
import com.tienphat.application.event.UpdateEventCommand;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.exception.InvalidEventScheduleException;
import com.tienphat.domain.exception.InvalidEventStateException;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import(EventDtoMapperImpl.class)
class EventControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant SALE_START = NOW.plus(1, ChronoUnit.DAYS);
    private static final Instant SALE_END = SALE_START.plus(7, ChronoUnit.DAYS);
    private static final Instant START = SALE_END.plus(1, ChronoUnit.DAYS);
    private static final Instant END = START.plus(3, ChronoUnit.HOURS);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UseCase<CreateEventCommand, EventResult> createEventUseCase;

    @MockitoBean
    private UseCase<UpdateEventCommand, EventResult> updateEventUseCase;

    @MockitoBean
    private UseCase<UUID, EventResult> getEventUseCase;

    @MockitoBean
    private UseCase<PageRequest, PageResult<EventResult>> listEventsUseCase;

    @MockitoBean
    private UseCase<DeactivateEventCommand, EventResult> deactivateEventUseCase;

    private static EventResult sampleResult(UUID id, EventStatus status) {
        return new EventResult(id, UUID.randomUUID(), "Concert", "A concert", "My Dinh Stadium",
                START, END, SALE_START, SALE_END, status, NOW, NOW);
    }

    private static Map<String, Object> validCreateBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizerId", UUID.randomUUID().toString());
        body.put("name", "Concert");
        body.put("description", "A concert");
        body.put("venueName", "My Dinh Stadium");
        body.put("startTime", START.toString());
        body.put("endTime", END.toString());
        body.put("saleStartTime", SALE_START.toString());
        body.put("saleEndTime", SALE_END.toString());
        return body;
    }

    private static Map<String, Object> validUpdateBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Concert v2");
        body.put("description", "Updated");
        body.put("venueName", "New Venue");
        body.put("startTime", START.toString());
        body.put("endTime", END.toString());
        body.put("saleStartTime", SALE_START.toString());
        body.put("saleEndTime", SALE_END.toString());
        return body;
    }

    @Nested
    @DisplayName("POST /api/v1/events")
    class CreateEvent {

        @Test
        @DisplayName("valid body -> 201, response matches mapped result, use-case invoked with matching command")
        void validBody_returns201() throws Exception {
            EventResult result = sampleResult(UUID.randomUUID(), EventStatus.DRAFT);
            when(createEventUseCase.execute(any())).thenReturn(result);
            Map<String, Object> body = validCreateBody();

            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(result.id().toString()))
                    .andExpect(jsonPath("$.status").value("DRAFT"));

            ArgumentCaptor<CreateEventCommand> captor = ArgumentCaptor.forClass(CreateEventCommand.class);
            verify(createEventUseCase).execute(captor.capture());
            assertThat(captor.getValue().organizerId()).isEqualTo(UUID.fromString((String) body.get("organizerId")));
            assertThat(captor.getValue().name()).isEqualTo("Concert");
        }

        @Test
        @DisplayName("malformed JSON body -> 400, use-case never invoked")
        void malformedJsonBody_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not valid json"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(createEventUseCase);
        }

        @Test
        @DisplayName("blank name -> 400, use-case never invoked")
        void blankName_returns400() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.put("name", "  ");

            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(createEventUseCase);
        }

        @Test
        @DisplayName("blank venueName -> 400, use-case never invoked")
        void blankVenueName_returns400() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.put("venueName", "");

            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(createEventUseCase);
        }

        @Test
        @DisplayName("null organizerId -> 400, use-case never invoked")
        void nullOrganizerId_returns400() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.put("organizerId", null);

            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(createEventUseCase);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/events/{id}")
    class GetEvent {

        @Test
        @DisplayName("resolvable id -> 200 with mapped body")
        void resolvableId_returns200() throws Exception {
            UUID id = UUID.randomUUID();
            EventResult result = sampleResult(id, EventStatus.PUBLISHED);
            when(getEventUseCase.execute(id)).thenReturn(result);

            mockMvc.perform(get("/api/v1/events/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));
        }

        @Test
        @DisplayName("unknown id -> 404")
        void unknownId_returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(getEventUseCase.execute(id)).thenThrow(new EventNotFoundException("Event " + id + " not found"));

            mockMvc.perform(get("/api/v1/events/{id}", id))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("malformed UUID path segment -> 400 via GlobalExceptionHandler")
        void malformedUuid_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/events/{id}", "not-a-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(getEventUseCase);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/events")
    class ListEvents {

        @Test
        @DisplayName("valid page/size -> 200, envelope matches mocked PageResult")
        void validPageAndSize_returns200() throws Exception {
            EventResult result = sampleResult(UUID.randomUUID(), EventStatus.ON_SALE);
            PageResult<EventResult> pageResult = new PageResult<>(List.of(result), 0, 10, 1);
            when(listEventsUseCase.execute(new PageRequest(0, 10))).thenReturn(pageResult);

            mockMvc.perform(get("/api/v1/events").param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(result.id().toString()))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("negative page -> 400, use-case never invoked")
        void negativePage_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/events").param("page", "-1").param("size", "10"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(listEventsUseCase);
        }

        @Test
        @DisplayName("zero size -> 400, use-case never invoked")
        void zeroSize_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/events").param("page", "0").param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(listEventsUseCase);
        }

        @Test
        @DisplayName("size above the 100 cap -> 400, use-case never invoked")
        void sizeAboveCap_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/events").param("page", "0").param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(listEventsUseCase);
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/events/{id}")
    class UpdateEvent {

        @Test
        @DisplayName("valid body -> 200, mapped body matches")
        void validBody_returns200() throws Exception {
            UUID id = UUID.randomUUID();
            EventResult result = sampleResult(id, EventStatus.DRAFT);
            when(updateEventUseCase.execute(any())).thenReturn(result);

            mockMvc.perform(put("/api/v1/events/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateBody())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()));

            ArgumentCaptor<UpdateEventCommand> captor = ArgumentCaptor.forClass(UpdateEventCommand.class);
            verify(updateEventUseCase).execute(captor.capture());
            assertThat(captor.getValue().id()).isEqualTo(id);
        }

        @Test
        @DisplayName("unknown id -> 404")
        void unknownId_returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(updateEventUseCase.execute(any()))
                    .thenThrow(new EventNotFoundException("Event " + id + " not found"));

            mockMvc.perform(put("/api/v1/events/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateBody())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("mocked InvalidEventScheduleException -> 400")
        void invalidSchedule_returns400() throws Exception {
            UUID id = UUID.randomUUID();
            when(updateEventUseCase.execute(any()))
                    .thenThrow(new InvalidEventScheduleException("startTime must be before endTime"));

            mockMvc.perform(put("/api/v1/events/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateBody())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/events/{id}/deactivate")
    class DeactivateEvent {

        @Test
        @DisplayName("valid id -> 200 with updated status")
        void validId_returns200() throws Exception {
            UUID id = UUID.randomUUID();
            EventResult result = sampleResult(id, EventStatus.CANCELLED);
            when(deactivateEventUseCase.execute(eq(new DeactivateEventCommand(id)))).thenReturn(result);

            mockMvc.perform(post("/api/v1/events/{id}/deactivate", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("unknown id -> 404")
        void unknownId_returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(deactivateEventUseCase.execute(any()))
                    .thenThrow(new EventNotFoundException("Event " + id + " not found"));

            mockMvc.perform(post("/api/v1/events/{id}/deactivate", id))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("mocked InvalidEventStateException (already CANCELLED) -> 400")
        void invalidState_returns400() throws Exception {
            UUID id = UUID.randomUUID();
            when(deactivateEventUseCase.execute(any()))
                    .thenThrow(new InvalidEventStateException("Event is already CANCELLED"));

            mockMvc.perform(post("/api/v1/events/{id}/deactivate", id))
                    .andExpect(status().isBadRequest());
        }
    }
}

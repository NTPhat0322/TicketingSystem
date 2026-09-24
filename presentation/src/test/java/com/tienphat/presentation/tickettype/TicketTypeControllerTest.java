package com.tienphat.presentation.tickettype;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienphat.application.event.EventResult;
import com.tienphat.application.tickettype.CreateTicketTypeCommand;
import com.tienphat.application.tickettype.DeactivateTicketTypeCommand;
import com.tienphat.application.tickettype.TicketTypeResult;
import com.tienphat.application.tickettype.UpdateTicketTypeCommand;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.presentation.config.SecurityConfig;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.exception.InvalidTicketTypeDataException;
import com.tienphat.domain.exception.TicketTypeConcurrentUpdateException;
import com.tienphat.domain.exception.TicketTypeNotAvailableException;
import com.tienphat.domain.exception.TicketTypeNotFoundException;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.model.TicketTypeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketTypeController.class)
@Import({SecurityConfig.class, TicketTypeDtoMapperImpl.class})
class TicketTypeControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("018f0f9e-0e39-7f31-9e13-ec7c1f160011");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UseCase<CreateTicketTypeCommand, TicketTypeResult> createTicketTypeUseCase;

    @MockitoBean
    private UseCase<UpdateTicketTypeCommand, TicketTypeResult> updateTicketTypeUseCase;

    @MockitoBean
    private UseCase<UUID, TicketTypeResult> getTicketTypeUseCase;

    @MockitoBean
    private UseCase<UUID, List<TicketTypeResult>> listTicketTypesByEventUseCase;

    @MockitoBean
    private UseCase<DeactivateTicketTypeCommand, TicketTypeResult> deactivateTicketTypeUseCase;

    @MockitoBean
    private UseCase<UUID, EventResult> getEventUseCase;

    private static TicketTypeResult sampleResult(UUID id, UUID eventId, TicketTypeStatus status) {
        return new TicketTypeResult(id, eventId, "VIP", BigDecimal.valueOf(150), 100, 10, 4, 900,
                0, status, NOW, NOW);
    }

    private static EventResult sampleEventResult(UUID eventId) {
        return new EventResult(eventId, UUID.randomUUID(), "Concert", "A concert", "My Dinh Stadium",
                NOW, NOW, NOW, NOW, EventStatus.PUBLISHED, NOW, NOW);
    }

    private static Map<String, Object> validCreateBody(UUID eventId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId.toString());
        body.put("name", "VIP");
        body.put("price", 150);
        body.put("totalQuantity", 100);
        body.put("maxPerUser", 4);
        body.put("holdDurationSec", 900);
        return body;
    }

    private static RequestPostProcessor jwtFor(String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(ACTOR_ID.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static Map<String, Object> validUpdateBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "VIP v2");
        body.put("price", 200);
        body.put("totalQuantity", 120);
        body.put("maxPerUser", 6);
        body.put("holdDurationSec", 600);
        return body;
    }

    @Nested
    @DisplayName("POST /api/v1/ticket-types")
    class CreateTicketType {

        @Test
        @DisplayName("valid body -> 201, response matches mapped result, use-case invoked with matching command")
        void validBody_returns201() throws Exception {
            UUID eventId = UUID.randomUUID();
            TicketTypeResult result = sampleResult(UUID.randomUUID(), eventId, TicketTypeStatus.ACTIVE);
            when(createTicketTypeUseCase.execute(any())).thenReturn(result);
            Map<String, Object> body = validCreateBody(eventId);

            mockMvc.perform(post("/api/v1/ticket-types")
                            .with(jwtFor("ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(result.id().toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            ArgumentCaptor<CreateTicketTypeCommand> captor = ArgumentCaptor.forClass(CreateTicketTypeCommand.class);
            verify(createTicketTypeUseCase).execute(captor.capture());
            assertThat(captor.getValue().eventId()).isEqualTo(eventId);
            assertThat(captor.getValue().name()).isEqualTo("VIP");
        }

        @Test
        @DisplayName("blank name -> 400, use-case never invoked")
        void blankName_returns400() throws Exception {
            Map<String, Object> body = validCreateBody(UUID.randomUUID());
            body.put("name", "  ");

            mockMvc.perform(post("/api/v1/ticket-types")
                            .with(jwtFor("ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(createTicketTypeUseCase);
        }

        @Test
        @DisplayName("negative price -> 400, use-case never invoked")
        void negativePrice_returns400() throws Exception {
            Map<String, Object> body = validCreateBody(UUID.randomUUID());
            body.put("price", -10);

            mockMvc.perform(post("/api/v1/ticket-types")
                            .with(jwtFor("ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(createTicketTypeUseCase);
        }

        @Test
        @DisplayName("non-positive totalQuantity -> 400, use-case never invoked")
        void nonPositiveTotalQuantity_returns400() throws Exception {
            Map<String, Object> body = validCreateBody(UUID.randomUUID());
            body.put("totalQuantity", 0);

            mockMvc.perform(post("/api/v1/ticket-types")
                            .with(jwtFor("ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(createTicketTypeUseCase);
        }

        @Test
        @DisplayName("mocked EventNotFoundException (eventId doesn't exist) -> 404")
        void unknownEventId_returns404() throws Exception {
            UUID eventId = UUID.randomUUID();
            when(createTicketTypeUseCase.execute(any()))
                    .thenThrow(new EventNotFoundException("Event " + eventId + " not found"));

            mockMvc.perform(post("/api/v1/ticket-types")
                            .with(jwtFor("ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateBody(eventId))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/ticket-types/{id}")
    class GetTicketType {

        @Test
        @DisplayName("resolvable id -> 200 with mapped body")
        void resolvableId_returns200() throws Exception {
            UUID id = UUID.randomUUID();
            TicketTypeResult result = sampleResult(id, UUID.randomUUID(), TicketTypeStatus.ACTIVE);
            when(getTicketTypeUseCase.execute(id)).thenReturn(result);

            mockMvc.perform(get("/api/v1/ticket-types/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()));
        }

        @Test
        @DisplayName("unknown id -> 404")
        void unknownId_returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(getTicketTypeUseCase.execute(id))
                    .thenThrow(new TicketTypeNotFoundException("TicketType " + id + " not found"));

            mockMvc.perform(get("/api/v1/ticket-types/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/ticket-types/{id}")
    class UpdateTicketType {

        @Test
        @DisplayName("valid body -> 200, mapped body matches")
        void validBody_returns200() throws Exception {
            UUID id = UUID.randomUUID();
            TicketTypeResult result = sampleResult(id, UUID.randomUUID(), TicketTypeStatus.ACTIVE);
            when(updateTicketTypeUseCase.execute(any())).thenReturn(result);

            mockMvc.perform(put("/api/v1/ticket-types/{id}", id)
                            .with(jwtFor("ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateBody())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()));

            ArgumentCaptor<UpdateTicketTypeCommand> captor = ArgumentCaptor.forClass(UpdateTicketTypeCommand.class);
            verify(updateTicketTypeUseCase).execute(captor.capture());
            assertThat(captor.getValue().id()).isEqualTo(id);
        }

        @Test
        @DisplayName("unknown id -> 404")
        void unknownId_returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(updateTicketTypeUseCase.execute(any()))
                    .thenThrow(new TicketTypeNotFoundException("TicketType " + id + " not found"));

            mockMvc.perform(put("/api/v1/ticket-types/{id}", id)
                            .with(jwtFor("ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateBody())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("mocked TicketTypeConcurrentUpdateException -> 409")
        void concurrentUpdate_returns409() throws Exception {
            UUID id = UUID.randomUUID();
            when(updateTicketTypeUseCase.execute(any()))
                    .thenThrow(new TicketTypeConcurrentUpdateException("TicketType " + id + " was updated concurrently"));

            mockMvc.perform(put("/api/v1/ticket-types/{id}", id)
                            .with(jwtFor("ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateBody())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("mocked InvalidTicketTypeDataException (totalQuantity below soldQuantity) -> 400")
        void invalidData_returns400() throws Exception {
            UUID id = UUID.randomUUID();
            when(updateTicketTypeUseCase.execute(any()))
                    .thenThrow(new InvalidTicketTypeDataException("totalQuantity cannot be below soldQuantity"));

            mockMvc.perform(put("/api/v1/ticket-types/{id}", id)
                            .with(jwtFor("ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateBody())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/ticket-types/{id}/deactivate")
    class DeactivateTicketType {

        @Test
        @DisplayName("valid id -> 200 with updated status")
        void validId_returns200() throws Exception {
            UUID id = UUID.randomUUID();
            TicketTypeResult result = sampleResult(id, UUID.randomUUID(), TicketTypeStatus.CLOSED);
            when(deactivateTicketTypeUseCase.execute(any())).thenReturn(result);

            mockMvc.perform(post("/api/v1/ticket-types/{id}/deactivate", id)
                            .with(jwtFor("ORGANIZER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        }

        @Test
        @DisplayName("unknown id -> 404")
        void unknownId_returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(deactivateTicketTypeUseCase.execute(any()))
                    .thenThrow(new TicketTypeNotFoundException("TicketType " + id + " not found"));

            mockMvc.perform(post("/api/v1/ticket-types/{id}/deactivate", id)
                            .with(jwtFor("ORGANIZER")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("mocked TicketTypeNotAvailableException (already CLOSED) -> 409")
        void alreadyClosed_returns409() throws Exception {
            UUID id = UUID.randomUUID();
            when(deactivateTicketTypeUseCase.execute(any()))
                    .thenThrow(new TicketTypeNotAvailableException("TicketType " + id + " is already CLOSED"));

            mockMvc.perform(post("/api/v1/ticket-types/{id}/deactivate", id)
                            .with(jwtFor("ORGANIZER")))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/events/{eventId}/ticket-types")
    class ListTicketTypesByEvent {

        @Test
        @DisplayName("resolvable eventId -> 200 with plain JSON array matching mocked list")
        void resolvableEventId_returns200() throws Exception {
            UUID eventId = UUID.randomUUID();
            when(getEventUseCase.execute(eventId)).thenReturn(sampleEventResult(eventId));
            TicketTypeResult result = sampleResult(UUID.randomUUID(), eventId, TicketTypeStatus.ACTIVE);
            when(listTicketTypesByEventUseCase.execute(eventId)).thenReturn(List.of(result));

            mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(result.id().toString()));
        }

        @Test
        @DisplayName("unknown eventId -> 404, listTicketTypesByEventUseCase never invoked")
        void unknownEventId_returns404() throws Exception {
            UUID eventId = UUID.randomUUID();
            when(getEventUseCase.execute(eventId))
                    .thenThrow(new EventNotFoundException("Event " + eventId + " not found"));

            mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId))
                    .andExpect(status().isNotFound());

            verifyNoInteractions(listTicketTypesByEventUseCase);
        }
    }
}

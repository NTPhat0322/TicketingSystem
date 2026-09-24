package com.tienphat.presentation.tickettype;

import com.tienphat.application.event.EventResult;
import com.tienphat.application.tickettype.CreateTicketTypeCommand;
import com.tienphat.application.tickettype.DeactivateTicketTypeCommand;
import com.tienphat.application.tickettype.TicketTypeResult;
import com.tienphat.application.tickettype.UpdateTicketTypeCommand;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.presentation.auth.JwtAuthorizationContext;
import com.tienphat.presentation.config.OpenApiConfig;
import com.tienphat.presentation.tickettype.dto.CreateTicketTypeRequest;
import com.tienphat.presentation.tickettype.dto.TicketTypeResponse;
import com.tienphat.presentation.tickettype.dto.UpdateTicketTypeRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TicketTypeController {

    private static final String BASE_PATH = "/api/v1/ticket-types";

    private final UseCase<CreateTicketTypeCommand, TicketTypeResult> createTicketTypeUseCase;
    private final UseCase<UpdateTicketTypeCommand, TicketTypeResult> updateTicketTypeUseCase;
    private final UseCase<UUID, TicketTypeResult> getTicketTypeUseCase;
    private final UseCase<UUID, List<TicketTypeResult>> listTicketTypesByEventUseCase;
    private final UseCase<DeactivateTicketTypeCommand, TicketTypeResult> deactivateTicketTypeUseCase;
    private final UseCase<UUID, EventResult> getEventUseCase;
    private final TicketTypeDtoMapper mapper;

    public TicketTypeController(
            UseCase<CreateTicketTypeCommand, TicketTypeResult> createTicketTypeUseCase,
            UseCase<UpdateTicketTypeCommand, TicketTypeResult> updateTicketTypeUseCase,
            UseCase<UUID, TicketTypeResult> getTicketTypeUseCase,
            UseCase<UUID, List<TicketTypeResult>> listTicketTypesByEventUseCase,
            UseCase<DeactivateTicketTypeCommand, TicketTypeResult> deactivateTicketTypeUseCase,
            UseCase<UUID, EventResult> getEventUseCase,
            TicketTypeDtoMapper mapper) {
        this.createTicketTypeUseCase = createTicketTypeUseCase;
        this.updateTicketTypeUseCase = updateTicketTypeUseCase;
        this.getTicketTypeUseCase = getTicketTypeUseCase;
        this.listTicketTypesByEventUseCase = listTicketTypesByEventUseCase;
        this.deactivateTicketTypeUseCase = deactivateTicketTypeUseCase;
        this.getEventUseCase = getEventUseCase;
        this.mapper = mapper;
    }

    @PostMapping(BASE_PATH)
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse create(@Valid @RequestBody CreateTicketTypeRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        TicketTypeResult result = createTicketTypeUseCase.execute(
                mapper.toCommand(request, JwtAuthorizationContext.from(jwt)));
        return mapper.toResponse(result);
    }

    @GetMapping(BASE_PATH + "/{id}")
    public TicketTypeResponse get(@PathVariable UUID id) {
        TicketTypeResult result = getTicketTypeUseCase.execute(id);
        return mapper.toResponse(result);
    }

    @PutMapping(BASE_PATH + "/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    public TicketTypeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTicketTypeRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        TicketTypeResult result = updateTicketTypeUseCase.execute(
                mapper.toCommand(id, request, JwtAuthorizationContext.from(jwt)));
        return mapper.toResponse(result);
    }

    @PostMapping(BASE_PATH + "/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    public TicketTypeResponse deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        TicketTypeResult result = deactivateTicketTypeUseCase.execute(
                new DeactivateTicketTypeCommand(id, JwtAuthorizationContext.from(jwt)));
        return mapper.toResponse(result);
    }

    @GetMapping("/api/v1/events/{eventId}/ticket-types")
    public List<TicketTypeResponse> listByEvent(@PathVariable UUID eventId) {
        getEventUseCase.execute(eventId);
        return listTicketTypesByEventUseCase.execute(eventId).stream().map(mapper::toResponse).toList();
    }
}

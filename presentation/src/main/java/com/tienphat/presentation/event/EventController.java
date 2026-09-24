package com.tienphat.presentation.event;

import com.tienphat.application.event.CreateEventCommand;
import com.tienphat.application.event.DeactivateEventCommand;
import com.tienphat.application.event.EventResult;
import com.tienphat.application.event.UpdateEventCommand;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.presentation.auth.JwtAuthorizationContext;
import com.tienphat.presentation.config.OpenApiConfig;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.presentation.dto.PageResponse;
import com.tienphat.presentation.event.dto.CreateEventRequest;
import com.tienphat.presentation.event.dto.EventResponse;
import com.tienphat.presentation.event.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/events")
public class EventController {

    private final UseCase<CreateEventCommand, EventResult> createEventUseCase;
    private final UseCase<UpdateEventCommand, EventResult> updateEventUseCase;
    private final UseCase<UUID, EventResult> getEventUseCase;
    private final UseCase<PageRequest, PageResult<EventResult>> listEventsUseCase;
    private final UseCase<DeactivateEventCommand, EventResult> deactivateEventUseCase;
    private final EventDtoMapper mapper;

    public EventController(
            UseCase<CreateEventCommand, EventResult> createEventUseCase,
            UseCase<UpdateEventCommand, EventResult> updateEventUseCase,
            UseCase<UUID, EventResult> getEventUseCase,
            UseCase<PageRequest, PageResult<EventResult>> listEventsUseCase,
            UseCase<DeactivateEventCommand, EventResult> deactivateEventUseCase,
            EventDtoMapper mapper) {
        this.createEventUseCase = createEventUseCase;
        this.updateEventUseCase = updateEventUseCase;
        this.getEventUseCase = getEventUseCase;
        this.listEventsUseCase = listEventsUseCase;
        this.deactivateEventUseCase = deactivateEventUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody CreateEventRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        EventResult result = createEventUseCase.execute(
                mapper.toCommand(request, JwtAuthorizationContext.from(jwt)));
        return mapper.toResponse(result);
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable UUID id) {
        EventResult result = getEventUseCase.execute(id);
        return mapper.toResponse(result);
    }

    @GetMapping
    public PageResponse<EventResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<EventResult> pageResult = listEventsUseCase.execute(new PageRequest(page, size));
        return mapper.toPageResponse(pageResult);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    public EventResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        EventResult result = updateEventUseCase.execute(
                mapper.toCommand(id, request, JwtAuthorizationContext.from(jwt)));
        return mapper.toResponse(result);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    public EventResponse deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        EventResult result = deactivateEventUseCase.execute(
                new DeactivateEventCommand(id, JwtAuthorizationContext.from(jwt)));
        return mapper.toResponse(result);
    }
}

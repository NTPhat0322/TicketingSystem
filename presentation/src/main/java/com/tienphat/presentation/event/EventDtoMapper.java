package com.tienphat.presentation.event;

import com.tienphat.application.event.CreateEventCommand;
import com.tienphat.application.event.EventResult;
import com.tienphat.application.event.UpdateEventCommand;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.presentation.dto.PageResponse;
import com.tienphat.presentation.event.dto.CreateEventRequest;
import com.tienphat.presentation.event.dto.EventResponse;
import com.tienphat.presentation.event.dto.UpdateEventRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface EventDtoMapper {

    CreateEventCommand toCommand(CreateEventRequest request);

    @Mapping(target = "id", source = "id")
    UpdateEventCommand toCommand(UUID id, UpdateEventRequest request);

    EventResponse toResponse(EventResult result);

    default PageResponse<EventResponse> toPageResponse(PageResult<EventResult> pageResult) {
        List<EventResponse> content = pageResult.content().stream().map(this::toResponse).toList();
        return new PageResponse<>(
                content, pageResult.page(), pageResult.size(), pageResult.totalElements(), pageResult.totalPages());
    }
}

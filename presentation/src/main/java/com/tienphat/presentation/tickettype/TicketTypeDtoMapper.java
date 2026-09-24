package com.tienphat.presentation.tickettype;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.tickettype.CreateTicketTypeCommand;
import com.tienphat.application.tickettype.TicketTypeResult;
import com.tienphat.application.tickettype.UpdateTicketTypeCommand;
import com.tienphat.presentation.tickettype.dto.CreateTicketTypeRequest;
import com.tienphat.presentation.tickettype.dto.TicketTypeResponse;
import com.tienphat.presentation.tickettype.dto.UpdateTicketTypeRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface TicketTypeDtoMapper {

    @Mapping(target = "actor", source = "actor")
    CreateTicketTypeCommand toCommand(CreateTicketTypeRequest request, AuthorizationContext actor);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "actor", source = "actor")
    UpdateTicketTypeCommand toCommand(UUID id, UpdateTicketTypeRequest request, AuthorizationContext actor);

    TicketTypeResponse toResponse(TicketTypeResult result);
}

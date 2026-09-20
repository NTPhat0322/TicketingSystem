package com.tienphat.application.tickettype;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.TicketTypeNotFoundException;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.repository.TicketTypeRepository;

import java.util.UUID;

public class GetTicketTypeUseCase implements UseCase<UUID, TicketTypeResult> {

    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTypeMapper ticketTypeMapper;

    public GetTicketTypeUseCase(TicketTypeRepository ticketTypeRepository, TicketTypeMapper ticketTypeMapper) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.ticketTypeMapper = ticketTypeMapper;
    }

    @Override
    public TicketTypeResult execute(UUID id) {
        TicketType ticketType = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new TicketTypeNotFoundException("TicketType " + id + " not found"));
        return ticketTypeMapper.toResult(ticketType);
    }
}

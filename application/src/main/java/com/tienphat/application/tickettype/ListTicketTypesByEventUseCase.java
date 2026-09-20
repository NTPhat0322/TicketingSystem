package com.tienphat.application.tickettype;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.repository.TicketTypeRepository;

import java.util.List;
import java.util.UUID;

public class ListTicketTypesByEventUseCase implements UseCase<UUID, List<TicketTypeResult>> {

    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTypeMapper ticketTypeMapper;

    public ListTicketTypesByEventUseCase(TicketTypeRepository ticketTypeRepository, TicketTypeMapper ticketTypeMapper) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.ticketTypeMapper = ticketTypeMapper;
    }

    @Override
    public List<TicketTypeResult> execute(UUID eventId) {
        return ticketTypeRepository.findAllByEventId(eventId).stream()
                .map(ticketTypeMapper::toResult)
                .toList();
    }
}

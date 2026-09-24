package com.tienphat.application.tickettype;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.exception.TicketTypeNotFoundException;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class DeactivateTicketTypeUseCase implements UseCase<DeactivateTicketTypeCommand, TicketTypeResult> {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final TicketTypeMapper ticketTypeMapper;

    public DeactivateTicketTypeUseCase(TicketTypeRepository ticketTypeRepository, EventRepository eventRepository,
                                       TicketTypeMapper ticketTypeMapper) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.eventRepository = eventRepository;
        this.ticketTypeMapper = ticketTypeMapper;
    }

    @Override
    public TicketTypeResult execute(DeactivateTicketTypeCommand command) {
        TicketType ticketType = ticketTypeRepository.findById(command.id())
                .orElseThrow(() -> new TicketTypeNotFoundException("TicketType " + command.id() + " not found"));
        var event = eventRepository.findById(ticketType.getEventId())
                .orElseThrow(() -> new EventNotFoundException("Event " + ticketType.getEventId() + " not found"));
        command.actor().requireCanManage(event.getOrganizerId());

        ticketType.close();

        TicketType saved = ticketTypeRepository.save(ticketType);
        return ticketTypeMapper.toResult(saved);
    }
}

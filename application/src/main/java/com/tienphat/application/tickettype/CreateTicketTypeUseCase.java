package com.tienphat.application.tickettype;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
public class CreateTicketTypeUseCase implements UseCase<CreateTicketTypeCommand, TicketTypeResult> {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final TicketTypeMapper ticketTypeMapper;

    public CreateTicketTypeUseCase(TicketTypeRepository ticketTypeRepository, EventRepository eventRepository,
                                   TicketTypeMapper ticketTypeMapper) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.eventRepository = eventRepository;
        this.ticketTypeMapper = ticketTypeMapper;
    }

    @Override
    public TicketTypeResult execute(CreateTicketTypeCommand command) {
        eventRepository.findById(command.eventId())
                .orElseThrow(() -> new EventNotFoundException("Event " + command.eventId() + " not found"));

        Money price = Money.of(command.price());
        TicketType ticketType = TicketType.create(UUID.randomUUID(), command.eventId(), command.name(), price,
                command.totalQuantity(), command.maxPerUser(), command.holdDurationSec());

        TicketType saved = ticketTypeRepository.save(ticketType);
        return ticketTypeMapper.toResult(saved);
    }
}

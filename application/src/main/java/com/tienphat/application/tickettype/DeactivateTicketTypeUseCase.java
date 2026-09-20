package com.tienphat.application.tickettype;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.TicketTypeNotFoundException;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class DeactivateTicketTypeUseCase implements UseCase<DeactivateTicketTypeCommand, TicketTypeResult> {

    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTypeMapper ticketTypeMapper;

    public DeactivateTicketTypeUseCase(TicketTypeRepository ticketTypeRepository, TicketTypeMapper ticketTypeMapper) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.ticketTypeMapper = ticketTypeMapper;
    }

    @Override
    public TicketTypeResult execute(DeactivateTicketTypeCommand command) {
        TicketType ticketType = ticketTypeRepository.findById(command.id())
                .orElseThrow(() -> new TicketTypeNotFoundException("TicketType " + command.id() + " not found"));

        ticketType.close();

        TicketType saved = ticketTypeRepository.save(ticketType);
        return ticketTypeMapper.toResult(saved);
    }
}

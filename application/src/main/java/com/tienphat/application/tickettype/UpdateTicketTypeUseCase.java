package com.tienphat.application.tickettype;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.TicketTypeNotFoundException;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class UpdateTicketTypeUseCase implements UseCase<UpdateTicketTypeCommand, TicketTypeResult> {

    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTypeMapper ticketTypeMapper;

    public UpdateTicketTypeUseCase(TicketTypeRepository ticketTypeRepository, TicketTypeMapper ticketTypeMapper) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.ticketTypeMapper = ticketTypeMapper;
    }

    @Override
    public TicketTypeResult execute(UpdateTicketTypeCommand command) {
        TicketType ticketType = ticketTypeRepository.findById(command.id())
                .orElseThrow(() -> new TicketTypeNotFoundException("TicketType " + command.id() + " not found"));

        Money price = Money.of(command.price());
        ticketType.updateDetails(command.name(), price, command.totalQuantity(),
                command.maxPerUser(), command.holdDurationSec());

        TicketType saved = ticketTypeRepository.save(ticketType);
        return ticketTypeMapper.toResult(saved);
    }
}

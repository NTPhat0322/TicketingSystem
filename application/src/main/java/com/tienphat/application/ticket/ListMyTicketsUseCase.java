package com.tienphat.application.ticket;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.model.Ticket;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.domain.repository.TicketRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class ListMyTicketsUseCase implements UseCase<ListMyTicketsCommand, PageResult<TicketResult>> {

    private final TicketRepository ticketRepository;
    private final TicketResultAssembler assembler;

    public ListMyTicketsUseCase(TicketRepository ticketRepository,
                                TicketTypeRepository ticketTypeRepository,
                                EventRepository eventRepository) {
        this.ticketRepository = ticketRepository;
        this.assembler = new TicketResultAssembler(ticketTypeRepository, eventRepository);
    }

    @Override
    public PageResult<TicketResult> execute(ListMyTicketsCommand command) {
        validateCommand(command);
        AuthorizationContext actor = command.actor();
        actor.requireAuthenticated();

        PageResult<Ticket> page = ticketRepository.findAllByOwnerUserId(actor.userId(), command.pageRequest());
        return new PageResult<>(assembler.assemble(page.content()), page.page(), page.size(), page.totalElements());
    }

    private static void validateCommand(ListMyTicketsCommand command) {
        if (command == null) {
            throw new InvalidReservationRequestException("My tickets command must not be null");
        }
        if (command.actor() == null) {
            throw new InvalidReservationRequestException("My tickets actor must not be null");
        }
        if (command.pageRequest() == null) {
            throw new InvalidReservationRequestException("My tickets pageRequest must not be null");
        }
    }
}

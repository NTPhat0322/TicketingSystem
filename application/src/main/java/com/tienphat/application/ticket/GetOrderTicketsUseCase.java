package com.tienphat.application.ticket;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.exception.OrderNotFoundException;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.Ticket;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.TicketRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Tickets exist only once the order is PAID; any other status yields an empty list. */
@Transactional(readOnly = true)
public class GetOrderTicketsUseCase implements UseCase<GetOrderTicketsCommand, List<TicketResult>> {

    private static final Comparator<Ticket> ISSUE_ORDER =
            Comparator.comparing(Ticket::getIssuedAt).thenComparing(Ticket::getTicketCode);

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TicketResultAssembler assembler;

    public GetOrderTicketsUseCase(OrderRepository orderRepository,
                                  TicketRepository ticketRepository,
                                  TicketTypeRepository ticketTypeRepository,
                                  EventRepository eventRepository) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.assembler = new TicketResultAssembler(ticketTypeRepository, eventRepository);
    }

    @Override
    public List<TicketResult> execute(GetOrderTicketsCommand command) {
        validateCommand(command);
        AuthorizationContext actor = command.actor();
        actor.requireAuthenticated();

        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order " + command.orderId() + " not found"));
        actor.requireCanAccess(order.getUserId());

        List<Ticket> tickets = order.getItems().stream()
                .flatMap(item -> ticketRepository.findAllByOrderItemId(item.getId()).stream())
                .sorted(ISSUE_ORDER)
                .toList();
        return assembler.assemble(tickets);
    }

    private static void validateCommand(GetOrderTicketsCommand command) {
        if (command == null) {
            throw new InvalidReservationRequestException("Order tickets command must not be null");
        }
        if (command.orderId() == null) {
            throw new InvalidReservationRequestException("Order tickets orderId must not be null");
        }
        if (command.actor() == null) {
            throw new InvalidReservationRequestException("Order tickets actor must not be null");
        }
    }
}

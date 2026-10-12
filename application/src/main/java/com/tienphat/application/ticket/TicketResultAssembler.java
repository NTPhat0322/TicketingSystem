package com.tienphat.application.ticket;

import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.Ticket;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.TicketTypeRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Enriches tickets with one batch lookup per table instead of one query per ticket. */
final class TicketResultAssembler {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;

    TicketResultAssembler(TicketTypeRepository ticketTypeRepository, EventRepository eventRepository) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.eventRepository = eventRepository;
    }

    List<TicketResult> assemble(List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            return List.of();
        }

        Set<UUID> ticketTypeIds = tickets.stream().map(Ticket::getTicketTypeId).collect(Collectors.toSet());
        Map<UUID, TicketType> ticketTypes = ticketTypeRepository.findAllByIds(ticketTypeIds).stream()
                .collect(Collectors.toMap(TicketType::getId, Function.identity()));
        Set<UUID> eventIds = ticketTypes.values().stream().map(TicketType::getEventId).collect(Collectors.toSet());
        Map<UUID, Event> events = eventRepository.findAllByIds(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        return tickets.stream()
                .map(ticket -> toResult(ticket, ticketTypes.get(ticket.getTicketTypeId()), events))
                .toList();
    }

    private static TicketResult toResult(Ticket ticket, TicketType ticketType, Map<UUID, Event> events) {
        Event event = ticketType == null ? null : events.get(ticketType.getEventId());
        return new TicketResult(
                ticket.getId(),
                ticket.getTicketCode(),
                ticket.getStatus(),
                ticket.getIssuedAt(),
                ticket.getCheckedInAt(),
                ticket.getOrderItemId(),
                ticket.getTicketTypeId(),
                ticketType == null ? null : ticketType.getName(),
                ticketType == null ? null : ticketType.getEventId(),
                event == null ? null : event.getName(),
                event == null ? null : event.getVenueName(),
                event == null ? null : event.getStartTime(),
                event == null ? null : event.getEndTime());
    }
}

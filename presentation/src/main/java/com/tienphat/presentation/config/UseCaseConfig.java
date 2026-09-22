package com.tienphat.presentation.config;

import com.tienphat.application.event.CreateEventCommand;
import com.tienphat.application.event.CreateEventUseCase;
import com.tienphat.application.event.DeactivateEventCommand;
import com.tienphat.application.event.DeactivateEventUseCase;
import com.tienphat.application.event.EventMapper;
import com.tienphat.application.event.EventResult;
import com.tienphat.application.event.GetEventUseCase;
import com.tienphat.application.event.ListEventsUseCase;
import com.tienphat.application.event.UpdateEventCommand;
import com.tienphat.application.event.UpdateEventUseCase;
import com.tienphat.application.tickettype.CreateTicketTypeCommand;
import com.tienphat.application.tickettype.CreateTicketTypeUseCase;
import com.tienphat.application.tickettype.DeactivateTicketTypeCommand;
import com.tienphat.application.tickettype.DeactivateTicketTypeUseCase;
import com.tienphat.application.tickettype.GetTicketTypeUseCase;
import com.tienphat.application.tickettype.ListTicketTypesByEventUseCase;
import com.tienphat.application.tickettype.TicketTypeMapper;
import com.tienphat.application.tickettype.TicketTypeResult;
import com.tienphat.application.tickettype.UpdateTicketTypeCommand;
import com.tienphat.application.tickettype.UpdateTicketTypeUseCase;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;

@Configuration
public class UseCaseConfig {

    @Bean
    public UseCase<CreateEventCommand, EventResult> createEventUseCase(
            EventRepository eventRepository, EventMapper eventMapper) {
        return new CreateEventUseCase(eventRepository, eventMapper);
    }

    @Bean
    public UseCase<UpdateEventCommand, EventResult> updateEventUseCase(
            EventRepository eventRepository, EventMapper eventMapper) {
        return new UpdateEventUseCase(eventRepository, eventMapper);
    }

    @Bean
    public UseCase<UUID, EventResult> getEventUseCase(EventRepository eventRepository, EventMapper eventMapper) {
        return new GetEventUseCase(eventRepository, eventMapper);
    }

    @Bean
    public UseCase<PageRequest, PageResult<EventResult>> listEventsUseCase(
            EventRepository eventRepository, EventMapper eventMapper) {
        return new ListEventsUseCase(eventRepository, eventMapper);
    }

    @Bean
    public UseCase<DeactivateEventCommand, EventResult> deactivateEventUseCase(
            EventRepository eventRepository, EventMapper eventMapper) {
        return new DeactivateEventUseCase(eventRepository, eventMapper);
    }

    @Bean
    public UseCase<CreateTicketTypeCommand, TicketTypeResult> createTicketTypeUseCase(
            TicketTypeRepository ticketTypeRepository, EventRepository eventRepository,
            TicketTypeMapper ticketTypeMapper) {
        return new CreateTicketTypeUseCase(ticketTypeRepository, eventRepository, ticketTypeMapper);
    }

    @Bean
    public UseCase<UpdateTicketTypeCommand, TicketTypeResult> updateTicketTypeUseCase(
            TicketTypeRepository ticketTypeRepository, TicketTypeMapper ticketTypeMapper) {
        return new UpdateTicketTypeUseCase(ticketTypeRepository, ticketTypeMapper);
    }

    @Bean
    public UseCase<UUID, TicketTypeResult> getTicketTypeUseCase(
            TicketTypeRepository ticketTypeRepository, TicketTypeMapper ticketTypeMapper) {
        return new GetTicketTypeUseCase(ticketTypeRepository, ticketTypeMapper);
    }

    @Bean
    public UseCase<UUID, List<TicketTypeResult>> listTicketTypesByEventUseCase(
            TicketTypeRepository ticketTypeRepository, TicketTypeMapper ticketTypeMapper) {
        return new ListTicketTypesByEventUseCase(ticketTypeRepository, ticketTypeMapper);
    }

    @Bean
    public UseCase<DeactivateTicketTypeCommand, TicketTypeResult> deactivateTicketTypeUseCase(
            TicketTypeRepository ticketTypeRepository, TicketTypeMapper ticketTypeMapper) {
        return new DeactivateTicketTypeUseCase(ticketTypeRepository, ticketTypeMapper);
    }
}

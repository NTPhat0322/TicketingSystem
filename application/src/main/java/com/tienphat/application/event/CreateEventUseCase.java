package com.tienphat.application.event;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.repository.EventRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
public class CreateEventUseCase implements UseCase<CreateEventCommand, EventResult> {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public CreateEventUseCase(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Override
    public EventResult execute(CreateEventCommand command) {
        Event event = Event.create(UUID.randomUUID(), command.organizerId(), command.name(),
                command.description(), command.venueName(), command.startTime(), command.endTime(),
                command.saleStartTime(), command.saleEndTime());

        Event saved = eventRepository.save(event);
        return eventMapper.toResult(saved);
    }
}

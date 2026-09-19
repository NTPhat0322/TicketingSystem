package com.tienphat.application.event;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.repository.EventRepository;

import java.util.UUID;

public class GetEventUseCase implements UseCase<UUID, EventResult> {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public GetEventUseCase(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Override
    public EventResult execute(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Event " + id + " not found"));
        return eventMapper.toResult(event);
    }
}

package com.tienphat.application.event;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.repository.EventRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class DeactivateEventUseCase implements UseCase<DeactivateEventCommand, EventResult> {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public DeactivateEventUseCase(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Override
    public EventResult execute(DeactivateEventCommand command) {
        Event event = eventRepository.findById(command.id())
                .orElseThrow(() -> new EventNotFoundException("Event " + command.id() + " not found"));

        event.cancel();

        Event saved = eventRepository.save(event);
        return eventMapper.toResult(saved);
    }
}

package com.tienphat.application.event;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.repository.EventRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class PublishEventUseCase implements UseCase<PublishEventCommand, EventResult> {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public PublishEventUseCase(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Override
    public EventResult execute(PublishEventCommand command) {
        Event event = eventRepository.findById(command.eventId())
                .orElseThrow(() -> new EventNotFoundException("Event " + command.eventId() + " not found"));

        command.actor().requireCanManage(event.getOrganizerId());
        event.publish();

        Event saved = eventRepository.save(event);
        return eventMapper.toResult(saved);
    }
}

package com.tienphat.application.event;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.repository.EventRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stops new reservations by moving the event to CLOSED. Redis stock and live holds are left alone:
 * pending orders can still be paid or expire normally.
 */
@Transactional
public class CloseSaleUseCase implements UseCase<CloseSaleCommand, EventResult> {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public CloseSaleUseCase(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Override
    public EventResult execute(CloseSaleCommand command) {
        Event event = eventRepository.findById(command.eventId())
                .orElseThrow(() -> new EventNotFoundException("Event " + command.eventId() + " not found"));

        command.actor().requireCanManage(event.getOrganizerId());
        event.close();

        Event saved = eventRepository.save(event);
        return eventMapper.toResult(saved);
    }
}

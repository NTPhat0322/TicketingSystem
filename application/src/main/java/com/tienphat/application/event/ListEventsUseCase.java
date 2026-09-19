package com.tienphat.application.event;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;

import java.util.List;

public class ListEventsUseCase implements UseCase<PageRequest, PageResult<EventResult>> {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public ListEventsUseCase(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Override
    public PageResult<EventResult> execute(PageRequest pageRequest) {
        PageResult<Event> page = eventRepository.findAll(pageRequest);

        List<EventResult> mapped = page.content().stream()
                .map(eventMapper::toResult)
                .toList();

        return new PageResult<>(mapped, page.page(), page.size(), page.totalElements());
    }
}

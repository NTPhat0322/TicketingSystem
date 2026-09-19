package com.tienphat.application.event;

import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListEventsUseCaseTest {

    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final Instant SALE_START = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Instant SALE_END = SALE_START.plus(7, ChronoUnit.DAYS);
    private static final Instant START = SALE_END.plus(1, ChronoUnit.DAYS);
    private static final Instant END = START.plus(3, ChronoUnit.HOURS);

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final EventMapper eventMapper = mock(EventMapper.class);
    private final ListEventsUseCase useCase = new ListEventsUseCase(eventRepository, eventMapper);

    @Test
    @DisplayName("execute() delegates paging to the repository and maps every item")
    void execute_delegatesAndMapsEachItem() {
        Event event1 = Event.create(UUID.randomUUID(), ORGANIZER_ID, "Concert 1", null, "Venue 1",
                START, END, SALE_START, SALE_END);
        Event event2 = Event.create(UUID.randomUUID(), ORGANIZER_ID, "Concert 2", null, "Venue 2",
                START, END, SALE_START, SALE_END);
        PageRequest pageRequest = new PageRequest(0, 10);
        PageResult<Event> domainPage = new PageResult<>(List.of(event1, event2), 0, 10, 2);
        when(eventRepository.findAll(pageRequest)).thenReturn(domainPage);

        EventResult result1 = new EventResult(event1.getId(), ORGANIZER_ID, "Concert 1", null, "Venue 1",
                START, END, SALE_START, SALE_END, EventStatus.DRAFT, Instant.now(), Instant.now());
        EventResult result2 = new EventResult(event2.getId(), ORGANIZER_ID, "Concert 2", null, "Venue 2",
                START, END, SALE_START, SALE_END, EventStatus.DRAFT, Instant.now(), Instant.now());
        when(eventMapper.toResult(event1)).thenReturn(result1);
        when(eventMapper.toResult(event2)).thenReturn(result2);

        PageResult<EventResult> result = useCase.execute(pageRequest);

        verify(eventRepository, times(1)).findAll(pageRequest);
        assertThat(result.content()).containsExactly(result1, result2);
        assertThat(result.totalElements()).isEqualTo(2);
    }
}

package com.tienphat.application.event;

import com.tienphat.domain.exception.InvalidEventDataException;
import com.tienphat.domain.exception.InvalidEventScheduleException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateEventUseCaseTest {

    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final Instant SALE_START = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Instant SALE_END = SALE_START.plus(7, ChronoUnit.DAYS);
    private static final Instant START = SALE_END.plus(1, ChronoUnit.DAYS);
    private static final Instant END = START.plus(3, ChronoUnit.HOURS);

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final EventMapper eventMapper = mock(EventMapper.class);
    private CreateEventUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateEventUseCase(eventRepository, eventMapper);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("execute() saves a new DRAFT event and returns the mapped result")
    void execute_savesAndReturnsDraftEvent() {
        CreateEventCommand command = new CreateEventCommand(ORGANIZER_ID, "Concert", "A concert",
                "My Dinh Stadium", START, END, SALE_START, SALE_END);
        EventResult expected = new EventResult(UUID.randomUUID(), ORGANIZER_ID, "Concert", "A concert",
                "My Dinh Stadium", START, END, SALE_START, SALE_END, EventStatus.DRAFT, Instant.now(), Instant.now());
        when(eventMapper.toResult(any(Event.class))).thenReturn(expected);

        EventResult result = useCase.execute(command);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        Event saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(saved.getOrganizerId()).isEqualTo(ORGANIZER_ID);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("execute() throws InvalidEventDataException on a blank name")
    void execute_throwsOnBlankName() {
        CreateEventCommand command = new CreateEventCommand(ORGANIZER_ID, " ", "A concert",
                "My Dinh Stadium", START, END, SALE_START, SALE_END);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidEventDataException.class);
    }

    @Test
    @DisplayName("execute() throws InvalidEventScheduleException on an inverted event window")
    void execute_throwsOnInvertedEventWindow() {
        CreateEventCommand command = new CreateEventCommand(ORGANIZER_ID, "Concert", "A concert",
                "My Dinh Stadium", END, START, SALE_START, SALE_END);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidEventScheduleException.class);
    }
}

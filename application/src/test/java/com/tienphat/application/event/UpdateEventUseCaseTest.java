package com.tienphat.application.event;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.exception.InvalidEventDataException;
import com.tienphat.domain.exception.InvalidEventScheduleException;
import com.tienphat.domain.exception.InvalidEventStateException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateEventUseCaseTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final AuthorizationContext ORGANIZER =
            new AuthorizationContext(ORGANIZER_ID, UserRole.ORGANIZER);
    private static final Instant SALE_START = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Instant SALE_END = SALE_START.plus(7, ChronoUnit.DAYS);
    private static final Instant START = SALE_END.plus(1, ChronoUnit.DAYS);
    private static final Instant END = START.plus(3, ChronoUnit.HOURS);

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final EventMapper eventMapper = mock(EventMapper.class);
    private UpdateEventUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateEventUseCase(eventRepository, eventMapper);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Event aDraft() {
        return Event.create(ID, ORGANIZER_ID, "Concert", "A concert", "My Dinh Stadium",
                START, END, SALE_START, SALE_END);
    }

    @Test
    @DisplayName("execute() applies new details and keeps DRAFT status")
    void execute_updatesDraftEvent() {
        Event event = aDraft();
        when(eventRepository.findById(ID)).thenReturn(Optional.of(event));
        EventResult expected = new EventResult(ID, ORGANIZER_ID, "New name", "New description",
                "New Venue", START, END, SALE_START, SALE_END, EventStatus.DRAFT, Instant.now(), Instant.now());
        when(eventMapper.toResult(any(Event.class))).thenReturn(expected);

        UpdateEventCommand command = new UpdateEventCommand(ID, ORGANIZER, "New name", "New description",
                "New Venue", START, END, SALE_START, SALE_END);

        EventResult result = useCase.execute(command);

        assertThat(event.getName()).isEqualTo("New name");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("execute() throws EventNotFoundException when the id does not exist")
    void execute_throwsWhenNotFound() {
        when(eventRepository.findById(ID)).thenReturn(Optional.empty());
        UpdateEventCommand command = new UpdateEventCommand(ID, ORGANIZER, "New name", "New description",
                "New Venue", START, END, SALE_START, SALE_END);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    @DisplayName("execute() throws InvalidEventStateException when the event has left DRAFT")
    void execute_throwsWhenNotDraft() {
        Event event = aDraft();
        event.publish();
        when(eventRepository.findById(ID)).thenReturn(Optional.of(event));
        UpdateEventCommand command = new UpdateEventCommand(ID, ORGANIZER, "New name", "New description",
                "New Venue", START, END, SALE_START, SALE_END);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    @DisplayName("execute() throws InvalidEventDataException on a blank name")
    void execute_throwsOnBlankName() {
        when(eventRepository.findById(ID)).thenReturn(Optional.of(aDraft()));
        UpdateEventCommand command = new UpdateEventCommand(ID, ORGANIZER, " ", "New description",
                "New Venue", START, END, SALE_START, SALE_END);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidEventDataException.class);
    }

    @Test
    @DisplayName("execute() throws InvalidEventScheduleException on an inverted event window")
    void execute_throwsOnInvertedEventWindow() {
        when(eventRepository.findById(ID)).thenReturn(Optional.of(aDraft()));
        UpdateEventCommand command = new UpdateEventCommand(ID, ORGANIZER, "New name", "New description",
                "New Venue", END, START, SALE_START, SALE_END);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidEventScheduleException.class);
    }
}

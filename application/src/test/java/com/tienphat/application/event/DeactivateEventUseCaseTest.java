package com.tienphat.application.event;

import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.exception.InvalidEventStateException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
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

class DeactivateEventUseCaseTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final Instant SALE_START = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Instant SALE_END = SALE_START.plus(7, ChronoUnit.DAYS);
    private static final Instant START = SALE_END.plus(1, ChronoUnit.DAYS);
    private static final Instant END = START.plus(3, ChronoUnit.HOURS);

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final EventMapper eventMapper = mock(EventMapper.class);
    private DeactivateEventUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeactivateEventUseCase(eventRepository, eventMapper);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Event aDraft() {
        return Event.create(ID, ORGANIZER_ID, "Concert", "A concert", "My Dinh Stadium",
                START, END, SALE_START, SALE_END);
    }

    @Test
    @DisplayName("execute() cancels the event and saves")
    void execute_cancelsAndSaves() {
        Event event = aDraft();
        when(eventRepository.findById(ID)).thenReturn(Optional.of(event));
        EventResult expected = new EventResult(ID, ORGANIZER_ID, "Concert", "A concert", "My Dinh Stadium",
                START, END, SALE_START, SALE_END, EventStatus.CANCELLED, Instant.now(), Instant.now());
        when(eventMapper.toResult(any(Event.class))).thenReturn(expected);

        EventResult result = useCase.execute(new DeactivateEventCommand(ID));

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("execute() throws EventNotFoundException when the id does not exist")
    void execute_throwsWhenNotFound() {
        when(eventRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new DeactivateEventCommand(ID)))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    @DisplayName("execute() throws InvalidEventStateException on an illegal transition")
    void execute_throwsOnIllegalTransition() {
        Event event = aDraft();
        event.publish();
        event.startSale();
        event.close();
        when(eventRepository.findById(ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> useCase.execute(new DeactivateEventCommand(ID)))
                .isInstanceOf(InvalidEventStateException.class);
    }
}

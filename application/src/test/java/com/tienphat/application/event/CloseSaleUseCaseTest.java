package com.tienphat.application.event;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.exception.InvalidEventStateException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloseSaleUseCaseTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final AuthorizationContext OWNER = new AuthorizationContext(ORGANIZER_ID, UserRole.ORGANIZER);
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final EventMapper eventMapper = mock(EventMapper.class);
    private CloseSaleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CloseSaleUseCase(eventRepository, eventMapper);
    }

    @Test
    @DisplayName("execute() closes an ON_SALE event owned by the organizer")
    void execute_closesOwnedOnSaleEvent() {
        Event event = anEvent(EventStatus.ON_SALE);
        EventResult expected = new EventResult(EVENT_ID, ORGANIZER_ID, "Concert", null, "Venue",
                event.getStartTime(), event.getEndTime(), event.getSaleStartTime(), event.getSaleEndTime(),
                EventStatus.CLOSED, NOW, NOW);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(eventMapper.toResult(event)).thenReturn(expected);

        EventResult result = useCase.execute(new CloseSaleCommand(EVENT_ID, OWNER));

        assertThat(result).isSameAs(expected);
        assertThat(event.getStatus()).isEqualTo(EventStatus.CLOSED);
        verify(eventRepository).save(event);
    }

    @Test
    @DisplayName("execute() lets ADMIN close any organizer's event")
    void execute_adminClosesForeignEvent() {
        Event event = anEvent(EventStatus.ON_SALE);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        useCase.execute(new CloseSaleCommand(EVENT_ID, new AuthorizationContext(UUID.randomUUID(), UserRole.ADMIN)));

        assertThat(event.getStatus()).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    @DisplayName("execute() rejects an organizer who does not own the event")
    void execute_rejectsForeignOrganizer() {
        Event event = anEvent(EventStatus.ON_SALE);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> useCase.execute(new CloseSaleCommand(EVENT_ID,
                new AuthorizationContext(UUID.randomUUID(), UserRole.ORGANIZER))))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.ON_SALE);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"DRAFT", "PUBLISHED", "CLOSED", "CANCELLED"})
    @DisplayName("execute() rejects an event that is not ON_SALE")
    void execute_rejectsEventNotOnSale(EventStatus status) {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(anEvent(status)));

        assertThatThrownBy(() -> useCase.execute(new CloseSaleCommand(EVENT_ID, OWNER)))
                .isInstanceOf(InvalidEventStateException.class);

        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("execute() reports an unknown event")
    void execute_rejectsUnknownEvent() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new CloseSaleCommand(EVENT_ID, OWNER)))
                .isInstanceOf(EventNotFoundException.class);

        verify(eventRepository, never()).save(any(Event.class));
    }

    private static Event anEvent(EventStatus status) {
        Instant saleStart = NOW.minus(1, ChronoUnit.HOURS);
        Instant saleEnd = NOW.plus(1, ChronoUnit.DAYS);
        Instant eventStart = NOW.plus(2, ChronoUnit.DAYS);
        return Event.reconstitute(EVENT_ID, ORGANIZER_ID, "Concert", null, "Venue",
                eventStart, eventStart.plus(3, ChronoUnit.HOURS), saleStart, saleEnd, status, NOW, NOW);
    }
}

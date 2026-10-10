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

class PublishEventUseCaseTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final EventMapper eventMapper = mock(EventMapper.class);
    private PublishEventUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PublishEventUseCase(eventRepository, eventMapper);
    }

    @Test
    @DisplayName("execute() publishes a DRAFT event owned by the organizer")
    void execute_publishesOwnedDraftEvent() {
        Event event = aDraftEvent();
        EventResult expected = new EventResult(EVENT_ID, ORGANIZER_ID, "Concert", null, "Venue",
                NOW.plus(2, ChronoUnit.DAYS), NOW.plus(2, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                NOW.plus(1, ChronoUnit.HOURS), NOW.plus(1, ChronoUnit.DAYS),
                EventStatus.PUBLISHED, event.getCreatedAt(), event.getUpdatedAt());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(eventMapper.toResult(event)).thenReturn(expected);

        EventResult result = useCase.execute(new PublishEventCommand(EVENT_ID,
                new AuthorizationContext(ORGANIZER_ID, UserRole.ORGANIZER)));

        assertThat(result.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        verify(eventRepository).save(event);
    }

    @Test
    @DisplayName("execute() rejects an organizer who does not own the event")
    void execute_rejectsForeignOrganizer() {
        Event event = aDraftEvent();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> useCase.execute(new PublishEventCommand(EVENT_ID,
                new AuthorizationContext(UUID.randomUUID(), UserRole.ORGANIZER))))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("execute() reports an unknown event")
    void execute_rejectsUnknownEvent() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new PublishEventCommand(EVENT_ID,
                new AuthorizationContext(ORGANIZER_ID, UserRole.ORGANIZER))))
                .isInstanceOf(EventNotFoundException.class);

        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("execute() rejects publishing an event that is no longer DRAFT")
    void execute_rejectsAlreadyPublishedEvent() {
        Event event = aDraftEvent();
        event.publish();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> useCase.execute(new PublishEventCommand(EVENT_ID,
                new AuthorizationContext(ORGANIZER_ID, UserRole.ORGANIZER))))
                .isInstanceOf(InvalidEventStateException.class);

        verify(eventRepository, never()).save(any(Event.class));
    }

    private static Event aDraftEvent() {
        Instant saleStart = NOW.plus(1, ChronoUnit.HOURS);
        Instant saleEnd = NOW.plus(1, ChronoUnit.DAYS);
        Instant eventStart = NOW.plus(2, ChronoUnit.DAYS);
        return Event.create(EVENT_ID, ORGANIZER_ID, "Concert", null, "Venue",
                eventStart, eventStart.plus(3, ChronoUnit.HOURS), saleStart, saleEnd);
    }
}

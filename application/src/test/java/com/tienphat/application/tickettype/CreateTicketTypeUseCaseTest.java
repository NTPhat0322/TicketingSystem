package com.tienphat.application.tickettype;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.exception.InvalidMoneyException;
import com.tienphat.domain.exception.InvalidTicketTypeDataException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateTicketTypeUseCaseTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final AuthorizationContext ORGANIZER =
            new AuthorizationContext(ORGANIZER_ID, UserRole.ORGANIZER);

    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final TicketTypeMapper ticketTypeMapper = mock(TicketTypeMapper.class);
    private CreateTicketTypeUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateTicketTypeUseCase(ticketTypeRepository, eventRepository, ticketTypeMapper);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(anEvent()));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("execute() saves a new ACTIVE ticket type with zero soldQuantity and returns the mapped result")
    void execute_savesAndReturnsActiveTicketType() {
        CreateTicketTypeCommand command = new CreateTicketTypeCommand(
                EVENT_ID, ORGANIZER, "VIP", new BigDecimal("500000"), 100, 4, 600);
        TicketTypeResult expected = new TicketTypeResult(UUID.randomUUID(), EVENT_ID, "VIP",
                new BigDecimal("500000.00"), 100, 0, 4, 600, 0, TicketTypeStatus.ACTIVE,
                Instant.now(), Instant.now());
        when(ticketTypeMapper.toResult(any(TicketType.class))).thenReturn(expected);

        TicketTypeResult result = useCase.execute(command);

        ArgumentCaptor<TicketType> captor = ArgumentCaptor.forClass(TicketType.class);
        verify(ticketTypeRepository).save(captor.capture());
        TicketType saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TicketTypeStatus.ACTIVE);
        assertThat(saved.getSoldQuantity()).isZero();
        assertThat(saved.getEventId()).isEqualTo(EVENT_ID);
        assertThat(result).isEqualTo(expected);
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute() throws InvalidMoneyException on a negative price before TicketType.create is reached")
    void execute_throwsOnNegativePrice() {
        CreateTicketTypeCommand command = new CreateTicketTypeCommand(
                EVENT_ID, ORGANIZER, "VIP", new BigDecimal("-1"), 100, 4, 600);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidMoneyException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute() throws InvalidTicketTypeDataException on a non-positive totalQuantity")
    void execute_throwsOnNonPositiveTotalQuantity() {
        CreateTicketTypeCommand command = new CreateTicketTypeCommand(
                EVENT_ID, ORGANIZER, "VIP", new BigDecimal("500000"), 0, 4, 600);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidTicketTypeDataException.class);
    }

    @Test
    @DisplayName("execute() throws EventNotFoundException when the referenced event does not exist")
    void execute_throwsWhenEventNotFound() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());
        CreateTicketTypeCommand command = new CreateTicketTypeCommand(
                EVENT_ID, ORGANIZER, "VIP", new BigDecimal("500000"), 100, 4, 600);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(EventNotFoundException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    private static Event anEvent() {
        Instant saleStart = Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS);
        Instant saleEnd = saleStart.plus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant start = saleEnd.plus(1, java.time.temporal.ChronoUnit.DAYS);
        Instant end = start.plus(3, java.time.temporal.ChronoUnit.HOURS);
        return Event.create(EVENT_ID, ORGANIZER_ID, "Concert", "desc", "Venue",
                start, end, saleStart, saleEnd);
    }
}

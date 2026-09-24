package com.tienphat.application.tickettype;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.exception.InvalidMoneyException;
import com.tienphat.domain.exception.InvalidTicketTypeDataException;
import com.tienphat.domain.exception.TicketTypeNotAvailableException;
import com.tienphat.domain.exception.TicketTypeNotFoundException;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateTicketTypeUseCaseTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final AuthorizationContext ORGANIZER =
            new AuthorizationContext(ORGANIZER_ID, UserRole.ORGANIZER);

    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final TicketTypeMapper ticketTypeMapper = mock(TicketTypeMapper.class);
    private UpdateTicketTypeUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateTicketTypeUseCase(ticketTypeRepository, eventRepository, ticketTypeMapper);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(anEvent()));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static TicketType anActive() {
        return TicketType.create(ID, EVENT_ID, "VIP", Money.of(new BigDecimal("500000")), 100, 4, 600);
    }

    @Test
    @DisplayName("execute() applies new details without touching soldQuantity or version")
    void execute_updatesFieldsAndReDerivesStatus() {
        TicketType type = anActive();
        type.confirmSale(4);
        when(ticketTypeRepository.findById(ID)).thenReturn(Optional.of(type));
        TicketTypeResult expected = new TicketTypeResult(ID, EVENT_ID, "VVIP", new BigDecimal("750000.00"),
                4, 4, 6, 900, 0, TicketTypeStatus.SOLD_OUT, type.getCreatedAt(), type.getUpdatedAt());
        when(ticketTypeMapper.toResult(any(TicketType.class))).thenReturn(expected);

        UpdateTicketTypeCommand command = new UpdateTicketTypeCommand(
                ID, ORGANIZER, "VVIP", new BigDecimal("750000"), 4, 6, 900);

        TicketTypeResult result = useCase.execute(command);

        assertThat(type.getName()).isEqualTo("VVIP");
        assertThat(type.getSoldQuantity()).isEqualTo(4);
        assertThat(type.getVersion()).isZero();
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.SOLD_OUT);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("execute() throws TicketTypeNotFoundException when the id does not exist")
    void execute_throwsWhenNotFound() {
        when(ticketTypeRepository.findById(ID)).thenReturn(Optional.empty());
        UpdateTicketTypeCommand command = new UpdateTicketTypeCommand(
                ID, ORGANIZER, "VVIP", new BigDecimal("750000"), 100, 4, 600);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TicketTypeNotFoundException.class);
    }

    @Test
    @DisplayName("execute() throws InvalidMoneyException on a negative new price")
    void execute_throwsOnNegativePrice() {
        when(ticketTypeRepository.findById(ID)).thenReturn(Optional.of(anActive()));
        UpdateTicketTypeCommand command = new UpdateTicketTypeCommand(
                ID, ORGANIZER, "VVIP", new BigDecimal("-1"), 100, 4, 600);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    @DisplayName("execute() throws InvalidTicketTypeDataException on a totalQuantity below soldQuantity")
    void execute_throwsWhenTotalQuantityBelowSoldQuantity() {
        TicketType type = anActive();
        type.confirmSale(10);
        when(ticketTypeRepository.findById(ID)).thenReturn(Optional.of(type));
        UpdateTicketTypeCommand command = new UpdateTicketTypeCommand(
                ID, ORGANIZER, "VVIP", new BigDecimal("750000"), 5, 4, 600);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidTicketTypeDataException.class);
    }

    @Test
    @DisplayName("execute() throws TicketTypeNotAvailableException when the ticket type is CLOSED")
    void execute_throwsWhenClosed() {
        TicketType type = anActive();
        type.close();
        when(ticketTypeRepository.findById(ID)).thenReturn(Optional.of(type));
        UpdateTicketTypeCommand command = new UpdateTicketTypeCommand(
                ID, ORGANIZER, "VVIP", new BigDecimal("750000"), 100, 4, 600);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TicketTypeNotAvailableException.class);
    }

    private static Event anEvent() {
        java.time.Instant saleStart = java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS);
        java.time.Instant saleEnd = saleStart.plus(7, java.time.temporal.ChronoUnit.DAYS);
        java.time.Instant start = saleEnd.plus(1, java.time.temporal.ChronoUnit.DAYS);
        java.time.Instant end = start.plus(3, java.time.temporal.ChronoUnit.HOURS);
        return Event.create(EVENT_ID, ORGANIZER_ID, "Concert", "desc", "Venue",
                start, end, saleStart, saleEnd);
    }
}

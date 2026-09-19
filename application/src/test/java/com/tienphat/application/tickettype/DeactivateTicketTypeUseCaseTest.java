package com.tienphat.application.tickettype;

import com.tienphat.domain.exception.TicketTypeNotAvailableException;
import com.tienphat.domain.exception.TicketTypeNotFoundException;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeactivateTicketTypeUseCaseTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final TicketTypeMapper ticketTypeMapper = mock(TicketTypeMapper.class);
    private DeactivateTicketTypeUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeactivateTicketTypeUseCase(ticketTypeRepository, ticketTypeMapper);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static TicketType anActive() {
        return TicketType.create(ID, EVENT_ID, "VIP", Money.of(new BigDecimal("500000")), 100, 4, 600);
    }

    @Test
    @DisplayName("execute() closes the ticket type and returns the mapped result")
    void execute_closesAndReturnsResult() {
        TicketType type = anActive();
        when(ticketTypeRepository.findById(ID)).thenReturn(Optional.of(type));
        TicketTypeResult expected = new TicketTypeResult(ID, EVENT_ID, "VIP", new BigDecimal("500000.00"),
                100, 0, 4, 600, 0, TicketTypeStatus.CLOSED, Instant.now(), Instant.now());
        when(ticketTypeMapper.toResult(any(TicketType.class))).thenReturn(expected);

        DeactivateTicketTypeCommand command = new DeactivateTicketTypeCommand(ID);
        TicketTypeResult result = useCase.execute(command);

        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.CLOSED);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("execute() throws TicketTypeNotFoundException when the id does not exist")
    void execute_throwsWhenNotFound() {
        when(ticketTypeRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new DeactivateTicketTypeCommand(ID)))
                .isInstanceOf(TicketTypeNotFoundException.class);
    }

    @Test
    @DisplayName("execute() throws TicketTypeNotAvailableException when the ticket type is already CLOSED")
    void execute_throwsWhenAlreadyClosed() {
        TicketType type = anActive();
        type.close();
        when(ticketTypeRepository.findById(ID)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> useCase.execute(new DeactivateTicketTypeCommand(ID)))
                .isInstanceOf(TicketTypeNotAvailableException.class);
    }
}

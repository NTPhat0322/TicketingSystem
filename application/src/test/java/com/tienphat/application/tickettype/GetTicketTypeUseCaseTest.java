package com.tienphat.application.tickettype;

import com.tienphat.domain.exception.TicketTypeNotFoundException;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetTicketTypeUseCaseTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final TicketTypeMapper ticketTypeMapper = mock(TicketTypeMapper.class);
    private final GetTicketTypeUseCase useCase = new GetTicketTypeUseCase(ticketTypeRepository, ticketTypeMapper);

    @Test
    @DisplayName("execute() returns the mapped result when the ticket type exists")
    void execute_returnsResult() {
        TicketType type = TicketType.create(ID, EVENT_ID, "VIP", Money.of(new BigDecimal("500000")), 100, 4, 600);
        when(ticketTypeRepository.findById(ID)).thenReturn(Optional.of(type));
        TicketTypeResult expected = new TicketTypeResult(ID, EVENT_ID, "VIP", new BigDecimal("500000.00"),
                100, 0, 4, 600, 0, TicketTypeStatus.ACTIVE, Instant.now(), Instant.now());
        when(ticketTypeMapper.toResult(type)).thenReturn(expected);

        TicketTypeResult result = useCase.execute(ID);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("execute() throws TicketTypeNotFoundException when the id does not exist")
    void execute_throwsWhenNotFound() {
        when(ticketTypeRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ID))
                .isInstanceOf(TicketTypeNotFoundException.class);
    }
}

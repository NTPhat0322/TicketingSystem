package com.tienphat.application.tickettype;

import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListTicketTypesByEventUseCaseTest {

    private static final UUID EVENT_ID = UUID.randomUUID();

    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final TicketTypeMapper ticketTypeMapper = mock(TicketTypeMapper.class);
    private final ListTicketTypesByEventUseCase useCase =
            new ListTicketTypesByEventUseCase(ticketTypeRepository, ticketTypeMapper);

    @Test
    @DisplayName("execute() returns the mapped results for every ticket type under the event")
    void execute_returnsMappedResults() {
        TicketType vip = TicketType.create(UUID.randomUUID(), EVENT_ID, "VIP", Money.of(new BigDecimal("500000")), 100, 4, 600);
        TicketType standard = TicketType.create(UUID.randomUUID(), EVENT_ID, "Standard", Money.of(new BigDecimal("200000")), 200, 4, 600);
        when(ticketTypeRepository.findAllByEventId(EVENT_ID)).thenReturn(List.of(vip, standard));
        TicketTypeResult vipResult = new TicketTypeResult(vip.getId(), EVENT_ID, "VIP", new BigDecimal("500000.00"),
                100, 0, 4, 600, 0, TicketTypeStatus.ACTIVE, Instant.now(), Instant.now());
        TicketTypeResult standardResult = new TicketTypeResult(standard.getId(), EVENT_ID, "Standard", new BigDecimal("200000.00"),
                200, 0, 4, 600, 0, TicketTypeStatus.ACTIVE, Instant.now(), Instant.now());
        when(ticketTypeMapper.toResult(vip)).thenReturn(vipResult);
        when(ticketTypeMapper.toResult(standard)).thenReturn(standardResult);

        List<TicketTypeResult> result = useCase.execute(EVENT_ID);

        assertThat(result).containsExactly(vipResult, standardResult);
        verify(ticketTypeRepository, times(1)).findAllByEventId(EVENT_ID);
    }

    @Test
    @DisplayName("execute() returns an empty list when the event has no ticket types")
    void execute_returnsEmptyList() {
        when(ticketTypeRepository.findAllByEventId(EVENT_ID)).thenReturn(List.of());

        List<TicketTypeResult> result = useCase.execute(EVENT_ID);

        assertThat(result).isEmpty();
    }
}

package com.tienphat.application.ticket;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.Ticket;
import com.tienphat.domain.model.TicketStatus;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.domain.repository.TicketRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListMyTicketsUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final AuthorizationContext ACTOR = new AuthorizationContext(USER_ID, UserRole.CUSTOMER);
    private static final PageRequest PAGE = new PageRequest(1, 2);

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private ListMyTicketsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListMyTicketsUseCase(ticketRepository, ticketTypeRepository, eventRepository);
    }

    @Test
    @DisplayName("execute() pages the caller's tickets and enriches them with type and event names")
    void execute_pagesCallerTickets() {
        Event event = GetOrderTicketsUseCaseTest.anEvent();
        TicketType vip = GetOrderTicketsUseCaseTest.aTicketType(event.getId(), "VIP");
        Ticket ticket = Ticket.reconstitute(UUID.randomUUID(), "TKT-1", UUID.randomUUID(), vip.getId(),
                USER_ID, TicketStatus.ISSUED, Instant.now(), null);
        when(ticketRepository.findAllByOwnerUserId(USER_ID, PAGE))
                .thenReturn(new PageResult<>(List.of(ticket), 1, 2, 3));
        when(ticketTypeRepository.findAllByIds(any())).thenReturn(List.of(vip));
        when(eventRepository.findAllByIds(any())).thenReturn(List.of(event));

        PageResult<TicketResult> result = useCase.execute(new ListMyTicketsCommand(ACTOR, PAGE));

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.content()).singleElement().satisfies(found -> {
            assertThat(found.ticketCode()).isEqualTo("TKT-1");
            assertThat(found.ticketTypeName()).isEqualTo("VIP");
            assertThat(found.eventName()).isEqualTo(event.getName());
        });
    }

    @Test
    @DisplayName("execute() rejects an unauthenticated caller before touching the repository")
    void execute_unauthenticatedIsForbidden() {
        assertThatThrownBy(() -> useCase.execute(
                new ListMyTicketsCommand(new AuthorizationContext(null, null), PAGE)))
                .isInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(ticketRepository);
    }
}

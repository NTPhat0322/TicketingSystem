package com.tienphat.application.ticket;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.exception.OrderNotFoundException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderItem;
import com.tienphat.domain.model.Ticket;
import com.tienphat.domain.model.TicketStatus;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.TicketRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetOrderTicketsUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final AuthorizationContext OWNER = new AuthorizationContext(USER_ID, UserRole.CUSTOMER);
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private GetOrderTicketsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOrderTicketsUseCase(orderRepository, ticketRepository, ticketTypeRepository, eventRepository);
    }

    @Test
    @DisplayName("execute() returns every item's tickets in issue order, enriched with one batch lookup per table")
    void execute_returnsEnrichedTicketsInIssueOrder() {
        Event event = anEvent();
        TicketType vip = aTicketType(event.getId(), "VIP");
        TicketType standard = aTicketType(event.getId(), "Standard");
        Order order = anOrder(vip.getId(), standard.getId());
        OrderItem vipItem = order.getItems().get(0);
        OrderItem standardItem = order.getItems().get(1);
        Ticket late = aTicket(vipItem, "TKT-B", NOW.plusSeconds(5));
        Ticket earlyB = aTicket(standardItem, "TKT-B", NOW);
        Ticket earlyA = aTicket(vipItem, "TKT-A", NOW);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(ticketRepository.findAllByOrderItemId(vipItem.getId())).thenReturn(List.of(late, earlyA));
        when(ticketRepository.findAllByOrderItemId(standardItem.getId())).thenReturn(List.of(earlyB));
        when(ticketTypeRepository.findAllByIds(any())).thenReturn(List.of(vip, standard));
        when(eventRepository.findAllByIds(any())).thenReturn(List.of(event));

        List<TicketResult> result = useCase.execute(new GetOrderTicketsCommand(order.getId(), OWNER));

        assertThat(result).extracting(TicketResult::ticketId)
                .containsExactly(earlyA.getId(), earlyB.getId(), late.getId());
        assertThat(result).extracting(TicketResult::ticketTypeName).containsExactly("VIP", "Standard", "VIP");
        assertThat(result).allSatisfy(ticket -> {
            assertThat(ticket.eventId()).isEqualTo(event.getId());
            assertThat(ticket.eventName()).isEqualTo(event.getName());
            assertThat(ticket.venueName()).isEqualTo(event.getVenueName());
            assertThat(ticket.eventStartTime()).isEqualTo(event.getStartTime());
        });
        verify(ticketTypeRepository, times(1)).findAllByIds(any());
        verify(eventRepository, times(1)).findAllByIds(any());
    }

    @Test
    @DisplayName("execute() leaves names null when the ticket type or its event no longer exists")
    void execute_missingTypeOrEventYieldsNullNames() {
        Event event = anEvent();
        TicketType known = aTicketType(event.getId(), "VIP");
        Order order = anOrder(known.getId(), UUID.randomUUID());
        Ticket withType = aTicket(order.getItems().get(0), "TKT-A", NOW);
        Ticket withoutType = aTicket(order.getItems().get(1), "TKT-B", NOW);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(ticketRepository.findAllByOrderItemId(order.getItems().get(0).getId())).thenReturn(List.of(withType));
        when(ticketRepository.findAllByOrderItemId(order.getItems().get(1).getId()))
                .thenReturn(List.of(withoutType));
        when(ticketTypeRepository.findAllByIds(any())).thenReturn(List.of(known));
        when(eventRepository.findAllByIds(any())).thenReturn(List.of());

        List<TicketResult> result = useCase.execute(new GetOrderTicketsCommand(order.getId(), OWNER));

        assertThat(result.get(0).ticketTypeName()).isEqualTo("VIP");
        assertThat(result.get(0).eventId()).isEqualTo(event.getId());
        assertThat(result.get(0).eventName()).isNull();
        assertThat(result.get(1).ticketTypeName()).isNull();
        assertThat(result.get(1).eventId()).isNull();
    }

    @Test
    @DisplayName("execute() returns an empty list for an order with no tickets yet without enrichment lookups")
    void execute_unpaidOrderReturnsEmpty() {
        Order order = anOrder(UUID.randomUUID(), UUID.randomUUID());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(ticketRepository.findAllByOrderItemId(any())).thenReturn(List.of());

        assertThat(useCase.execute(new GetOrderTicketsCommand(order.getId(), OWNER))).isEmpty();
        verifyNoInteractions(ticketTypeRepository, eventRepository);
    }

    @Test
    @DisplayName("execute() lets ADMIN read another user's tickets")
    void execute_adminCanReadForeignOrder() {
        Order order = anOrder(UUID.randomUUID(), UUID.randomUUID());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(ticketRepository.findAllByOrderItemId(any())).thenReturn(List.of());

        AuthorizationContext admin = new AuthorizationContext(UUID.randomUUID(), UserRole.ADMIN);
        assertThat(useCase.execute(new GetOrderTicketsCommand(order.getId(), admin))).isEmpty();
    }

    @Test
    @DisplayName("execute() forbids another customer before loading tickets")
    void execute_foreignCustomerIsForbidden() {
        Order order = anOrder(UUID.randomUUID(), UUID.randomUUID());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        AuthorizationContext stranger = new AuthorizationContext(UUID.randomUUID(), UserRole.CUSTOMER);
        assertThatThrownBy(() -> useCase.execute(new GetOrderTicketsCommand(order.getId(), stranger)))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(ticketRepository);
    }

    @Test
    @DisplayName("execute() reports an unknown order as not found")
    void execute_missingOrderIsNotFound() {
        UUID orderId = Order.generateId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetOrderTicketsCommand(orderId, OWNER)))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private static Order anOrder(UUID firstTicketTypeId, UUID secondTicketTypeId) {
        Order order = Order.create(Order.generateId(), "ORD-" + UUID.randomUUID(), USER_ID, UUID.randomUUID(),
                NOW, NOW.plusSeconds(300));
        order.addItem(firstTicketTypeId, 2, Money.of(new BigDecimal("150000")));
        order.addItem(secondTicketTypeId, 1, Money.of(new BigDecimal("90000")));
        return order;
    }

    private static Ticket aTicket(OrderItem item, String ticketCode, Instant issuedAt) {
        return Ticket.reconstitute(UUID.randomUUID(), ticketCode, item.getId(), item.getTicketTypeId(),
                USER_ID, TicketStatus.ISSUED, issuedAt, null);
    }

    static TicketType aTicketType(UUID eventId, String name) {
        return TicketType.reconstitute(UUID.randomUUID(), eventId, name, Money.of(new BigDecimal("150000")),
                100, 10, 4, 300, 0, TicketTypeStatus.ACTIVE, NOW, NOW);
    }

    static Event anEvent() {
        Instant start = NOW.plusSeconds(86_400);
        return Event.reconstitute(UUID.randomUUID(), UUID.randomUUID(), "Rock Night", "desc", "Hall A",
                start, start.plusSeconds(7_200), NOW.minusSeconds(3_600), start, EventStatus.ON_SALE, NOW, NOW);
    }
}

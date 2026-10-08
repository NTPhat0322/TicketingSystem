package com.tienphat.application.event;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.exception.InvalidEventStateException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.port.StockCachePort;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartSaleUseCaseTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final AuthorizationContext ORGANIZER =
            new AuthorizationContext(ORGANIZER_ID, UserRole.ORGANIZER);
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final StockCachePort stockCachePort = mock(StockCachePort.class);
    private final EventMapper eventMapper = mock(EventMapper.class);
    private StartSaleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new StartSaleUseCase(eventRepository, ticketTypeRepository, stockCachePort, eventMapper);
    }

    @Test
    @DisplayName("execute() warms every ticket type before moving the event to ON_SALE")
    void execute_warmsBeforeStartingSale() {
        Event event = aPublishedEvent();
        TicketType vip = TicketType.create(UUID.randomUUID(), EVENT_ID, "VIP",
                Money.of(new BigDecimal("150000")), 100, 4, 300);
        TicketType standard = TicketType.create(UUID.randomUUID(), EVENT_ID, "Standard",
                Money.of(new BigDecimal("80000")), 200, 6, 300);
        EventResult expected = new EventResult(EVENT_ID, ORGANIZER_ID, "Concert", null, "Venue",
                event.getStartTime(), event.getEndTime(), event.getSaleStartTime(), event.getSaleEndTime(),
                EventStatus.ON_SALE, event.getCreatedAt(), event.getUpdatedAt());

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findAllByEventId(EVENT_ID)).thenReturn(List.of(vip, standard));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventMapper.toResult(any(Event.class))).thenReturn(expected);
        doAnswer(invocation -> {
            assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
            return null;
        }).when(stockCachePort).warmUp(any(UUID.class), any(Integer.class));

        EventResult result = useCase.execute(new StartSaleCommand(EVENT_ID, ORGANIZER));

        assertThat(result).isEqualTo(expected);
        assertThat(event.getStatus()).isEqualTo(EventStatus.ON_SALE);
        verify(stockCachePort).warmUp(vip.getId(), vip.getTotalQuantity());
        verify(stockCachePort).warmUp(standard.getId(), standard.getTotalQuantity());
        InOrder order = org.mockito.Mockito.inOrder(stockCachePort, eventRepository);
        order.verify(stockCachePort).warmUp(vip.getId(), vip.getTotalQuantity());
        order.verify(stockCachePort).warmUp(standard.getId(), standard.getTotalQuantity());
        order.verify(eventRepository).save(event);
    }

    @Test
    @DisplayName("execute() does not start or save the event when a warm-up fails")
    void execute_doesNotTransitionWhenWarmUpFails() {
        Event event = aPublishedEvent();
        TicketType vip = TicketType.create(UUID.randomUUID(), EVENT_ID, "VIP",
                Money.of(new BigDecimal("150000")), 100, 4, 300);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findAllByEventId(EVENT_ID)).thenReturn(List.of(vip));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(stockCachePort).warmUp(vip.getId(), vip.getTotalQuantity());

        assertThatThrownBy(() -> useCase.execute(new StartSaleCommand(EVENT_ID, ORGANIZER)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("execute() rejects an already-on-sale event before warming missing Redis stock")
    void execute_rejectsAlreadyOnSaleEventBeforeWarming() {
        Event event = aPublishedEvent();
        event.startSale();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> useCase.execute(new StartSaleCommand(EVENT_ID, ORGANIZER)))
                .isInstanceOf(InvalidEventStateException.class);

        verify(stockCachePort, never()).warmUp(any(UUID.class), any(Integer.class));
        verify(ticketTypeRepository, never()).findAllByEventId(any(UUID.class));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("execute() enforces organizer ownership before warming stock")
    void execute_rejectsForeignOrganizer() {
        Event event = aPublishedEvent();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> useCase.execute(new StartSaleCommand(
                EVENT_ID, new AuthorizationContext(UUID.randomUUID(), UserRole.ORGANIZER))))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(stockCachePort, never()).warmUp(any(UUID.class), any(Integer.class));
        verify(eventRepository, never()).save(any(Event.class));
    }

    private static Event aPublishedEvent() {
        Instant saleStart = NOW.plus(1, ChronoUnit.HOURS);
        Instant saleEnd = NOW.plus(1, ChronoUnit.DAYS);
        Instant eventStart = NOW.plus(2, ChronoUnit.DAYS);
        Event event = Event.create(EVENT_ID, ORGANIZER_ID, "Concert", null, "Venue",
                eventStart, eventStart.plus(3, ChronoUnit.HOURS), saleStart, saleEnd);
        event.publish();
        return event;
    }
}

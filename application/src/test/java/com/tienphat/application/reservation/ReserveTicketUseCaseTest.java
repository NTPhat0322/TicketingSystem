package com.tienphat.application.reservation;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.order.OrderTrackingStatus;
import com.tienphat.domain.exception.EventNotOnSaleException;
import com.tienphat.domain.exception.InvalidReservationQuantityException;
import com.tienphat.domain.exception.ReservationLimitExceededException;
import com.tienphat.domain.exception.ReservationServiceUnavailableException;
import com.tienphat.domain.exception.StockNotWarmedException;
import com.tienphat.domain.exception.TicketTypeSoldOutException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.port.ReservationRequest;
import com.tienphat.domain.port.ReservationResult;
import com.tienphat.domain.port.StockCachePort;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

class ReserveTicketUseCaseTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final AuthorizationContext ACTOR = new AuthorizationContext(USER_ID, UserRole.CUSTOMER);

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final TicketTypeRepository ticketTypeRepository = mock(TicketTypeRepository.class);
    private final StockCachePort stockCachePort = mock(StockCachePort.class);
    private ReserveTicketUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReserveTicketUseCase(eventRepository, ticketTypeRepository, stockCachePort,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(anOnSaleEvent()));
        when(ticketTypeRepository.findById(TICKET_TYPE_ID)).thenReturn(Optional.of(anActiveTicketType()));
    }

    @Test
    @DisplayName("execute() generates one UUIDv7, carries the price/expiry snapshot, and returns CREATING")
    void execute_reservesWithPreGeneratedOrderIdAndSnapshot() {
        when(stockCachePort.tryReserve(any(ReservationRequest.class))).thenReturn(ReservationResult.SUCCESS);

        ReserveTicketResult result = useCase.execute(
                new ReserveTicketCommand(EVENT_ID, TICKET_TYPE_ID, 2, ACTOR));

        ArgumentCaptor<ReservationRequest> captor = ArgumentCaptor.forClass(ReservationRequest.class);
        verify(stockCachePort).tryReserve(captor.capture());
        ReservationRequest request = captor.getValue();

        assertThat(result.status()).isEqualTo(OrderTrackingStatus.CREATING);
        assertThat(result.orderId()).isEqualTo(request.orderId());
        assertThat(result.orderId().version()).isEqualTo(7);
        assertThat(request.userId()).isEqualTo(USER_ID);
        assertThat(request.eventId()).isEqualTo(EVENT_ID);
        assertThat(request.ticketTypeId()).isEqualTo(TICKET_TYPE_ID);
        assertThat(request.quantity()).isEqualTo(2);
        assertThat(request.unitPrice()).isEqualTo(Money.of(new BigDecimal("150000")));
        assertThat(request.reservedAt()).isEqualTo(NOW);
        assertThat(request.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(result.unitPrice()).isEqualByComparingTo("150000.00");
        assertThat(result.expiresAt()).isEqualTo(request.expiresAt());
    }

    @Test
    @DisplayName("execute() never touches Redis for a non-positive quantity")
    void execute_rejectsNonPositiveQuantityBeforeReservation() {
        assertThatThrownBy(() -> useCase.execute(
                new ReserveTicketCommand(EVENT_ID, TICKET_TYPE_ID, 0, ACTOR)))
                .isInstanceOf(InvalidReservationQuantityException.class);

        verify(stockCachePort, never()).tryReserve(any());
    }

    @Test
    @DisplayName("execute() rejects an event that is not ON_SALE")
    void execute_rejectsEventNotOnSale() {
        Event published = anOnSaleEvent();
        published.close();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> useCase.execute(
                new ReserveTicketCommand(EVENT_ID, TICKET_TYPE_ID, 1, ACTOR)))
                .isInstanceOf(EventNotOnSaleException.class);

        verify(stockCachePort, never()).tryReserve(any());
    }

    @Test
    @DisplayName("execute() maps atomic result failures to stable business errors")
    void execute_mapsReservationFailures() {
        when(stockCachePort.tryReserve(any(ReservationRequest.class)))
                .thenReturn(ReservationResult.OUT_OF_STOCK);
        assertThatThrownBy(() -> reserveOne())
                .isInstanceOf(TicketTypeSoldOutException.class);

        when(stockCachePort.tryReserve(any(ReservationRequest.class)))
                .thenReturn(ReservationResult.USER_LIMIT_EXCEEDED);
        assertThatThrownBy(() -> reserveOne())
                .isInstanceOf(ReservationLimitExceededException.class);

        when(stockCachePort.tryReserve(any(ReservationRequest.class)))
                .thenReturn(ReservationResult.STOCK_NOT_WARMED);
        assertThatThrownBy(() -> reserveOne())
                .isInstanceOf(StockNotWarmedException.class);
    }

    @Test
    @DisplayName("execute() fails closed when Redis reservation is unavailable")
    void execute_rejectsWhenReservationServiceIsUnavailable() {
        IllegalStateException redisFailure = new IllegalStateException("redis down");
        when(stockCachePort.tryReserve(any(ReservationRequest.class))).thenThrow(redisFailure);

        assertThatThrownBy(this::reserveOne)
                .isInstanceOf(ReservationServiceUnavailableException.class)
                .hasMessageContaining("reservation was not accepted")
                .hasCauseReference(redisFailure);
    }

    @Test
    @DisplayName("execute() rejects a request larger than maxPerUser before Redis")
    void execute_rejectsQuantityAbovePerUserLimit() {
        assertThatThrownBy(() -> useCase.execute(
                new ReserveTicketCommand(EVENT_ID, TICKET_TYPE_ID, 5, ACTOR)))
                .isInstanceOf(ReservationLimitExceededException.class);

        verify(stockCachePort, never()).tryReserve(any());
    }

    private void reserveOne() {
        useCase.execute(new ReserveTicketCommand(EVENT_ID, TICKET_TYPE_ID, 1, ACTOR));
    }

    private static Event anOnSaleEvent() {
        Instant saleStart = NOW.minus(1, ChronoUnit.HOURS);
        Instant saleEnd = NOW.plus(1, ChronoUnit.DAYS);
        Instant eventStart = NOW.plus(2, ChronoUnit.DAYS);
        Event event = Event.create(EVENT_ID, UUID.randomUUID(), "Concert", null, "Venue",
                eventStart, eventStart.plus(3, ChronoUnit.HOURS), saleStart, saleEnd);
        event.publish();
        event.startSale();
        return event;
    }

    private static TicketType anActiveTicketType() {
        return TicketType.create(TICKET_TYPE_ID, EVENT_ID, "VIP", Money.of(new BigDecimal("150000")),
                10, 4, 300);
    }
}

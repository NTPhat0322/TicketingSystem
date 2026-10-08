package com.tienphat.application.reservation;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.order.OrderTrackingStatus;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.exception.EventNotOnSaleException;
import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.exception.InvalidReservationQuantityException;
import com.tienphat.domain.exception.ReservationLimitExceededException;
import com.tienphat.domain.exception.ReservationServiceUnavailableException;
import com.tienphat.domain.exception.StockNotWarmedException;
import com.tienphat.domain.exception.TicketTypeNotAvailableException;
import com.tienphat.domain.exception.TicketTypeNotFoundException;
import com.tienphat.domain.exception.TicketTypeSoldOutException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.port.ReservationRequest;
import com.tienphat.domain.port.ReservationResult;
import com.tienphat.domain.port.StockCachePort;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Validates the sale snapshot and performs the one atomic reservation call.
 *
 * <p>No Order is saved here. The generated UUID is returned in {@code CREATING} state and is later
 * reused by the relay/worker when the PostgreSQL Order is created.
 */
@Transactional(readOnly = true)
public class ReserveTicketUseCase implements UseCase<ReserveTicketCommand, ReserveTicketResult> {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final StockCachePort stockCachePort;
    private final Clock clock;

    public ReserveTicketUseCase(EventRepository eventRepository,
                                TicketTypeRepository ticketTypeRepository,
                                StockCachePort stockCachePort) {
        this(eventRepository, ticketTypeRepository, stockCachePort, Clock.systemUTC());
    }

    public ReserveTicketUseCase(EventRepository eventRepository,
                                TicketTypeRepository ticketTypeRepository,
                                StockCachePort stockCachePort,
                                Clock clock) {
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.stockCachePort = stockCachePort;
        this.clock = clock;
    }

    @Override
    public ReserveTicketResult execute(ReserveTicketCommand command) {
        validateCommand(command);
        AuthorizationContext actor = command.actor();
        actor.requireAuthenticated();

        if (command.quantity() <= 0) {
            throw new InvalidReservationQuantityException(
                    "Reservation quantity must be positive, but was " + command.quantity());
        }

        Event event = eventRepository.findById(command.eventId())
                .orElseThrow(() -> new EventNotFoundException(
                        "Event " + command.eventId() + " not found"));
        if (event.getStatus() != EventStatus.ON_SALE) {
            throw new EventNotOnSaleException(
                    "Event " + event.getId() + " is " + event.getStatus() + ", not ON_SALE");
        }

        TicketType ticketType = ticketTypeRepository.findById(command.ticketTypeId())
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        "TicketType " + command.ticketTypeId() + " not found"));
        if (!event.getId().equals(ticketType.getEventId())) {
            throw new InvalidReservationRequestException(
                    "TicketType " + ticketType.getId() + " does not belong to Event " + event.getId());
        }
        if (ticketType.getStatus() == TicketTypeStatus.SOLD_OUT
                || ticketType.getTotalQuantity() <= ticketType.getSoldQuantity()) {
            throw new TicketTypeSoldOutException("TicketType " + ticketType.getId() + " is sold out");
        }
        if (ticketType.getStatus() != TicketTypeStatus.ACTIVE) {
            throw new TicketTypeNotAvailableException(
                    "TicketType " + ticketType.getId() + " is " + ticketType.getStatus());
        }
        if (command.quantity() > ticketType.getMaxPerUser()) {
            throw new ReservationLimitExceededException(
                    "Reservation quantity exceeds maxPerUser " + ticketType.getMaxPerUser());
        }

        Instant reservedAt = clock.instant();
        Instant expiresAt = reservedAt.plusSeconds(ticketType.getHoldDurationSec());
        ReservationRequest request = new ReservationRequest(
                Order.generateId(),
                actor.userId(),
                event.getId(),
                ticketType.getId(),
                command.quantity(),
                ticketType.getPrice(),
                ticketType.getMaxPerUser(),
                ticketType.getHoldDurationSec(),
                reservedAt,
                expiresAt);

        ReservationResult result;
        try {
            result = Objects.requireNonNull(
                    stockCachePort.tryReserve(request),
                    "StockCachePort.tryReserve must not return null");
        } catch (RuntimeException exception) {
            throw new ReservationServiceUnavailableException(
                    "Reservation service is unavailable; reservation was not accepted", exception);
        }
        switch (result) {
            case SUCCESS -> {
                return new ReserveTicketResult(
                        request.orderId(),
                        request.userId(),
                        OrderTrackingStatus.CREATING,
                        request.eventId(),
                        request.ticketTypeId(),
                        request.quantity(),
                        request.unitPrice().getAmount(),
                        request.reservedAt(),
                        request.expiresAt());
            }
            case OUT_OF_STOCK -> throw new TicketTypeSoldOutException(
                    "TicketType " + ticketType.getId() + " is sold out");
            case USER_LIMIT_EXCEEDED -> throw new ReservationLimitExceededException(
                    "User " + actor.userId() + " exceeded the limit for TicketType " + ticketType.getId());
            case STOCK_NOT_WARMED -> throw new StockNotWarmedException(
                    "TicketType " + ticketType.getId() + " stock has not been warmed");
        }
        throw new IllegalStateException("Unhandled reservation result " + result);
    }

    private static void validateCommand(ReserveTicketCommand command) {
        if (command == null) {
            throw new InvalidReservationRequestException("Reservation command must not be null");
        }
        if (command.eventId() == null) {
            throw new InvalidReservationRequestException("Reservation eventId must not be null");
        }
        if (command.ticketTypeId() == null) {
            throw new InvalidReservationRequestException("Reservation ticketTypeId must not be null");
        }
        if (command.actor() == null) {
            throw new InvalidReservationRequestException("Reservation actor must not be null");
        }
    }
}

package com.tienphat.application.event;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.port.StockCachePort;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Warms all sale-time inventory before moving the Event to ON_SALE. */
@Transactional
public class StartSaleUseCase implements UseCase<StartSaleCommand, EventResult> {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final StockCachePort stockCachePort;
    private final EventMapper eventMapper;

    public StartSaleUseCase(EventRepository eventRepository,
                            TicketTypeRepository ticketTypeRepository,
                            StockCachePort stockCachePort,
                            EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.stockCachePort = stockCachePort;
        this.eventMapper = eventMapper;
    }

    @Override
    public EventResult execute(StartSaleCommand command) {
        validateCommand(command);

        Event event = eventRepository.findById(command.eventId())
                .orElseThrow(() -> new EventNotFoundException(
                        "Event " + command.eventId() + " not found"));
        command.actor().requireCanManage(event.getOrganizerId());
        event.ensureCanStartSale();

        List<TicketType> ticketTypes = ticketTypeRepository.findAllByEventId(event.getId());
        for (TicketType ticketType : ticketTypes) {
            stockCachePort.warmUp(ticketType.getId(), ticketType.getTotalQuantity());
        }

        // The state transition is deliberately after every warm-up call. A failed warm-up leaves
        // the Event out of ON_SALE and therefore keeps reservations fail-closed.
        event.startSale();
        Event saved = eventRepository.save(event);
        return eventMapper.toResult(saved);
    }

    private static void validateCommand(StartSaleCommand command) {
        if (command == null) {
            throw new InvalidReservationRequestException("Start-sale command must not be null");
        }
        if (command.eventId() == null) {
            throw new InvalidReservationRequestException("Start-sale eventId must not be null");
        }
        if (command.actor() == null) {
            throw new InvalidReservationRequestException("Start-sale actor must not be null");
        }
    }
}

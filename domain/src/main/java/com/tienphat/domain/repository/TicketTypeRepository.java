package com.tienphat.domain.repository;

import com.tienphat.domain.model.TicketType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link TicketType} persistence. Owned by the domain, implemented in {@code infrastructure}.
 *
 * <p>{@code TicketType} is its own aggregate root rather than a child of {@code Event}, so it has its
 * own repository.
 */
public interface TicketTypeRepository {

    TicketType save(TicketType ticketType);

    Optional<TicketType> findById(UUID id);

    List<TicketType> findAllByEventId(UUID eventId);
}

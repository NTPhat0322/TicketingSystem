package com.tienphat.domain.repository;

import com.tienphat.domain.model.Ticket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link Ticket} persistence. Owned by the domain, implemented in {@code infrastructure}.
 *
 * <p>Three lookups because three different callers exist: {@code findById} for internal references,
 * {@code findByTicketCode} for the gate scanning a QR code, and {@code findAllByOrderItemId} for the
 * payment flow, which issues one ticket per unit of quantity and needs to read them back as a group.
 */
public interface TicketRepository {

    Ticket save(Ticket ticket);

    Optional<Ticket> findById(UUID id);

    Optional<Ticket> findByTicketCode(String ticketCode);

    List<Ticket> findAllByOrderItemId(UUID orderItemId);
}

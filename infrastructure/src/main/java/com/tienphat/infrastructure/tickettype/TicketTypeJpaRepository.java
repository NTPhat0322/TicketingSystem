package com.tienphat.infrastructure.tickettype;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TicketTypeJpaRepository extends JpaRepository<TicketTypeJpaEntity, UUID> {

    List<TicketTypeJpaEntity> findAllByEventId(UUID eventId);
}

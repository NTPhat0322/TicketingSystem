package com.tienphat.infrastructure.tickettype;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface TicketTypeJpaRepository extends JpaRepository<TicketTypeJpaEntity, UUID> {

    List<TicketTypeJpaEntity> findAllByEventId(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketTypeJpaEntity t where t.id = :id")
    java.util.Optional<TicketTypeJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}

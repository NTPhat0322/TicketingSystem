package com.tienphat.infrastructure.ticket;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TicketJpaRepository extends JpaRepository<TicketJpaEntity, UUID> {

    Optional<TicketJpaEntity> findByTicketCode(String ticketCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketJpaEntity t where t.ticketCode = :ticketCode")
    Optional<TicketJpaEntity> findByTicketCodeForUpdate(@Param("ticketCode") String ticketCode);

    List<TicketJpaEntity> findAllByOrderItemId(UUID orderItemId);
}

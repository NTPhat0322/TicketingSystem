package com.tienphat.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * The caller must keep this query inside the transaction that publishes and updates the rows.
     * SKIP LOCKED lets another publisher take different pending rows without waiting or claiming
     * the same row.
     */
    @Query(value = """
            select *
            from outbox_events
            where status = 'PENDING'
            order by created_at asc, id asc
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> findAllPendingForUpdate();
}

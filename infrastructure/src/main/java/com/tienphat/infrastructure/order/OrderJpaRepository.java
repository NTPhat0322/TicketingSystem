package com.tienphat.infrastructure.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<OrderJpaEntity> findById(UUID id);

    @EntityGraph(attributePaths = "items")
    Optional<OrderJpaEntity> findByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct o from OrderJpaEntity o left join fetch o.items where o.id = :id")
    Optional<OrderJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}

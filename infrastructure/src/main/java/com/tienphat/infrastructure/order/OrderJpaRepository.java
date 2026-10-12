package com.tienphat.infrastructure.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    /** Pages ids only: a fetch join on {@code items} would make Hibernate paginate in memory. */
    @Query(value = "select o.id from OrderJpaEntity o where o.userId = :userId"
            + " order by o.createdAt desc, o.id desc",
            countQuery = "select count(o) from OrderJpaEntity o where o.userId = :userId")
    Page<UUID> findIdsByUserId(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    List<OrderJpaEntity> findAllByIdIn(Collection<UUID> ids);
}

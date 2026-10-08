package com.tienphat.infrastructure.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentJpaEntity p where p.id = :id")
    Optional<PaymentJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<PaymentJpaEntity> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentJpaEntity p where p.orderId = :orderId")
    Optional<PaymentJpaEntity> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    Optional<PaymentJpaEntity> findByTransactionRef(String transactionRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentJpaEntity p where p.transactionRef = :transactionRef")
    Optional<PaymentJpaEntity> findByTransactionRefForUpdate(@Param("transactionRef") String transactionRef);
}

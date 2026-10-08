package com.tienphat.infrastructure.payment;

import com.tienphat.domain.model.Payment;
import com.tienphat.domain.repository.PaymentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for payment attempts, including callback-safe row locks. */
@Repository
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentPersistenceMapper mapper;

    public PaymentRepositoryImpl(PaymentJpaRepository jpaRepository, PaymentPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity saved = jpaRepository.saveAndFlush(mapper.toEntity(payment));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Payment> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Payment> findByOrderIdForUpdate(UUID orderId) {
        return jpaRepository.findByOrderIdForUpdate(orderId).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByTransactionRef(String transactionRef) {
        return jpaRepository.findByTransactionRef(transactionRef).map(mapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Payment> findByTransactionRefForUpdate(String transactionRef) {
        return jpaRepository.findByTransactionRefForUpdate(transactionRef).map(mapper::toDomain);
    }
}

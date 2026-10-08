package com.tienphat.infrastructure.outbox;

import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.repository.OutboxEventRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for the transactional outbox. */
@Repository
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;
    private final OutboxEventPersistenceMapper mapper;

    public OutboxEventRepositoryImpl(
            OutboxEventJpaRepository jpaRepository,
            OutboxEventPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventJpaEntity saved = jpaRepository.saveAndFlush(mapper.toEntity(event));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<OutboxEvent> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    /**
     * The surrounding transaction must also publish and mark rows before it commits; otherwise the
     * row lock would be released immediately after this method returns.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboxEvent> findAllPending() {
        return jpaRepository.findAllPendingForUpdate().stream()
                .map(mapper::toDomain)
                .toList();
    }
}

package com.tienphat.infrastructure.tickettype;

import com.tienphat.domain.exception.TicketTypeConcurrentUpdateException;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.repository.TicketTypeRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TicketTypeRepositoryImpl implements TicketTypeRepository {

    private final TicketTypeJpaRepository jpaRepository;
    private final TicketTypePersistenceMapper mapper;

    public TicketTypeRepositoryImpl(TicketTypeJpaRepository jpaRepository, TicketTypePersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public TicketType save(TicketType ticketType) {
        try {
            TicketTypeJpaEntity saved = jpaRepository.save(mapper.toEntity(ticketType));
            return mapper.toDomain(saved);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new TicketTypeConcurrentUpdateException(
                    "TicketType " + ticketType.getId() + " was updated concurrently; stale version");
        }
    }

    @Override
    public Optional<TicketType> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TicketType> findAllByEventId(UUID eventId) {
        return jpaRepository.findAllByEventId(eventId).stream().map(mapper::toDomain).toList();
    }
}

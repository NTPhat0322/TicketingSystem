package com.tienphat.infrastructure.ticket;

import com.tienphat.domain.model.Ticket;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.domain.repository.TicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for issued tickets. */
@Repository
public class TicketRepositoryImpl implements TicketRepository {

    private static final Sort NEWEST_FIRST =
            Sort.by("issuedAt").descending().and(Sort.by("id").descending());

    private final TicketJpaRepository jpaRepository;
    private final TicketPersistenceMapper mapper;

    public TicketRepositoryImpl(TicketJpaRepository jpaRepository, TicketPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Ticket save(Ticket ticket) {
        TicketJpaEntity saved = jpaRepository.saveAndFlush(mapper.toEntity(ticket));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Ticket> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Ticket> findByTicketCode(String ticketCode) {
        return jpaRepository.findByTicketCode(ticketCode).map(mapper::toDomain);
    }

    @Override
    public List<Ticket> findAllByOrderItemId(UUID orderItemId) {
        return jpaRepository.findAllByOrderItemId(orderItemId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public PageResult<Ticket> findAllByOwnerUserId(UUID ownerUserId, PageRequest pageRequest) {
        Page<TicketJpaEntity> page = jpaRepository.findAllByOwnerUserId(
                ownerUserId,
                org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size(), NEWEST_FIRST));
        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                pageRequest.page(),
                pageRequest.size(),
                page.getTotalElements());
    }

    /** Infrastructure helper for a future gate/check-in flow. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Ticket> findByTicketCodeForUpdate(String ticketCode) {
        return jpaRepository.findByTicketCodeForUpdate(ticketCode).map(mapper::toDomain);
    }
}

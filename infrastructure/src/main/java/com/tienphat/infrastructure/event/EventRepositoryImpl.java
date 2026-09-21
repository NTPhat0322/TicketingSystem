package com.tienphat.infrastructure.event;

import com.tienphat.domain.model.Event;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class EventRepositoryImpl implements EventRepository {

    private static final Sort DETERMINISTIC_SORT =
            Sort.by("createdAt").ascending().and(Sort.by("id").ascending());

    private final EventJpaRepository jpaRepository;
    private final EventPersistenceMapper mapper;

    public EventRepositoryImpl(EventJpaRepository jpaRepository, EventPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Event save(Event event) {
        EventJpaEntity saved = jpaRepository.save(mapper.toEntity(event));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<Event> findAll(PageRequest pageRequest) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(), pageRequest.size(), DETERMINISTIC_SORT);
        Page<EventJpaEntity> page = jpaRepository.findAll(pageable);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                pageRequest.page(),
                pageRequest.size(),
                page.getTotalElements());
    }
}

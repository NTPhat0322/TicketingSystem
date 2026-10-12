package com.tienphat.domain.repository;

import com.tienphat.domain.model.Event;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link Event} persistence. Owned by the domain, implemented in {@code infrastructure}.
 */
public interface EventRepository {

    Event save(Event event);

    Optional<Event> findById(UUID id);

    PageResult<Event> findAll(PageRequest pageRequest);

    /** Batch lookup; unknown ids are skipped rather than reported. */
    List<Event> findAllByIds(Collection<UUID> ids);
}

package com.tienphat.domain.repository;

import com.tienphat.domain.model.Event;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link Event} persistence. Owned by the domain, implemented in {@code infrastructure}.
 */
public interface EventRepository {

    Event save(Event event);

    Optional<Event> findById(UUID id);
}

package com.tienphat.infrastructure.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface EventJpaRepository extends JpaRepository<EventJpaEntity, UUID> {
}

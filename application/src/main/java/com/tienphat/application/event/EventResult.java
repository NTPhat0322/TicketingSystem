package com.tienphat.application.event;

import com.tienphat.domain.model.EventStatus;

import java.time.Instant;
import java.util.UUID;

public record EventResult(
        UUID id,
        UUID organizerId,
        String name,
        String description,
        String venueName,
        Instant startTime,
        Instant endTime,
        Instant saleStartTime,
        Instant saleEndTime,
        EventStatus status,
        Instant createdAt,
        Instant updatedAt) {
}

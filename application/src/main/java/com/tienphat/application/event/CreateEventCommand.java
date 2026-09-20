package com.tienphat.application.event;

import java.time.Instant;
import java.util.UUID;

public record CreateEventCommand(
        UUID organizerId,
        String name,
        String description,
        String venueName,
        Instant startTime,
        Instant endTime,
        Instant saleStartTime,
        Instant saleEndTime) {
}

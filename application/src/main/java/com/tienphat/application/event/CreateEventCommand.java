package com.tienphat.application.event;

import com.tienphat.application.auth.AuthorizationContext;

import java.time.Instant;

public record CreateEventCommand(
        AuthorizationContext actor,
        String name,
        String description,
        String venueName,
        Instant startTime,
        Instant endTime,
        Instant saleStartTime,
        Instant saleEndTime) {
}

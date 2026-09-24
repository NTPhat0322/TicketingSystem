package com.tienphat.presentation.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateEventRequest(
        @NotBlank String name,
        String description,
        @NotBlank String venueName,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull Instant saleStartTime,
        @NotNull Instant saleEndTime) {
}

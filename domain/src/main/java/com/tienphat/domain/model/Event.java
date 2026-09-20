package com.tienphat.domain.model;

import com.tienphat.domain.exception.InvalidEventDataException;
import com.tienphat.domain.exception.InvalidEventScheduleException;
import com.tienphat.domain.exception.InvalidEventStateException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * A show that tickets are sold for. An aggregate root in its own right; {@link TicketType} is a
 * sibling aggregate, not a child held here.
 *
 * <p>Follows the entity pattern set by {@link User}: private all-args constructor (without it
 * {@code @Builder} would leave a package-private one open), private builder behind a named factory,
 * no setters, identity-based equality.
 *
 * <p>Every status change delegates to {@link EventStatus#canTransitionTo}, so the transition table
 * stays the only place the lifecycle is described.
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public class Event {

    private final UUID id;
    private final UUID organizerId;
    private String name;
    private String description;
    private String venueName;
    private Instant startTime;
    private Instant endTime;
    private Instant saleStartTime;
    private Instant saleEndTime;
    private EventStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Event(UUID id, UUID organizerId, String name, String description, String venueName,
                  Instant startTime, Instant endTime, Instant saleStartTime, Instant saleEndTime,
                  EventStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizerId = organizerId;
        this.name = name;
        this.description = description;
        this.venueName = venueName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.saleStartTime = saleStartTime;
        this.saleEndTime = saleEndTime;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates an event in {@link EventStatus#DRAFT}. {@code description} is optional; every other
     * field is required.
     *
     * <p>Schedule validation is deliberately narrow: it only enforces that each pair is ordered
     * ({@code startTime < endTime}, {@code saleStartTime < saleEndTime}). It does not require sales
     * to end before the doors open — design doc §4 does not state that as an invariant, and
     * at-the-door sales are a real case.
     */
    public static Event create(UUID id, UUID organizerId, String name, String description,
                               String venueName, Instant startTime, Instant endTime,
                               Instant saleStartTime, Instant saleEndTime) {
        requireNotNull(id, "id");
        requireNotNull(organizerId, "organizerId");
        requireNotBlank(name, "name");
        requireNotBlank(venueName, "venueName");
        requireNotNull(startTime, "startTime");
        requireNotNull(endTime, "endTime");
        requireNotNull(saleStartTime, "saleStartTime");
        requireNotNull(saleEndTime, "saleEndTime");

        if (!startTime.isBefore(endTime)) {
            throw new InvalidEventScheduleException("Event startTime must be before endTime");
        }
        if (!saleStartTime.isBefore(saleEndTime)) {
            throw new InvalidEventScheduleException("Event saleStartTime must be before saleEndTime");
        }

        Instant now = Instant.now();
        return Event.builder()
                .id(id)
                .organizerId(organizerId)
                .name(name)
                .description(description)
                .venueName(venueName)
                .startTime(startTime)
                .endTime(endTime)
                .saleStartTime(saleStartTime)
                .saleEndTime(saleEndTime)
                .status(EventStatus.DRAFT)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Rebuilds an event that already exists in storage. For persistence mappers only — application
     * code creating a new event calls {@link #create}.
     *
     * <p>Null checks only, no schedule or state validation: the row was valid when written, and
     * re-checking would make a later rule change unreadable history. This is also the only way to
     * land directly in a status such as {@code CANCELLED} without replaying the transitions that
     * originally produced it.
     */
    public static Event reconstitute(UUID id, UUID organizerId, String name, String description,
                                     String venueName, Instant startTime, Instant endTime,
                                     Instant saleStartTime, Instant saleEndTime, EventStatus status,
                                     Instant createdAt, Instant updatedAt) {
        requireNotNull(id, "id");
        requireNotNull(organizerId, "organizerId");
        requireNotNull(name, "name");
        requireNotNull(venueName, "venueName");
        requireNotNull(startTime, "startTime");
        requireNotNull(endTime, "endTime");
        requireNotNull(saleStartTime, "saleStartTime");
        requireNotNull(saleEndTime, "saleEndTime");
        requireNotNull(status, "status");
        requireNotNull(createdAt, "createdAt");
        requireNotNull(updatedAt, "updatedAt");

        return Event.builder()
                .id(id)
                .organizerId(organizerId)
                .name(name)
                .description(description)
                .venueName(venueName)
                .startTime(startTime)
                .endTime(endTime)
                .saleStartTime(saleStartTime)
                .saleEndTime(saleEndTime)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * Applies new field values while still {@link EventStatus#DRAFT}. Runs the exact same field
     * and schedule validation as {@link #create}; once an event has left {@code DRAFT} no field is
     * editable.
     */
    public void updateDetails(String name, String description, String venueName,
                              Instant startTime, Instant endTime,
                              Instant saleStartTime, Instant saleEndTime) {
        if (status != EventStatus.DRAFT) {
            throw new InvalidEventStateException(
                    "Event details can only be updated while DRAFT, was " + status);
        }

        requireNotBlank(name, "name");
        requireNotBlank(venueName, "venueName");
        requireNotNull(startTime, "startTime");
        requireNotNull(endTime, "endTime");
        requireNotNull(saleStartTime, "saleStartTime");
        requireNotNull(saleEndTime, "saleEndTime");

        if (!startTime.isBefore(endTime)) {
            throw new InvalidEventScheduleException("Event startTime must be before endTime");
        }
        if (!saleStartTime.isBefore(saleEndTime)) {
            throw new InvalidEventScheduleException("Event saleStartTime must be before saleEndTime");
        }

        this.name = name;
        this.description = description;
        this.venueName = venueName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.saleStartTime = saleStartTime;
        this.saleEndTime = saleEndTime;
        this.updatedAt = Instant.now();
    }

    /** {@code DRAFT → PUBLISHED}. The event becomes visible; tickets are not on sale yet. */
    public void publish() {
        transitionTo(EventStatus.PUBLISHED);
    }

    /**
     * {@code PUBLISHED → ON_SALE}.
     *
     * <p>Stays a pure in-memory status change. Design doc §2.4 makes Redis stock warming a
     * precondition for selling, but the domain owns no port to do it with, and §6 has not settled
     * whether warming is triggered here or by a scheduled job reading {@code saleStartTime}. Either
     * way the orchestration belongs to the application layer's {@code StartSaleUseCase}: warm
     * first, then call this.
     */
    public void startSale() {
        transitionTo(EventStatus.ON_SALE);
    }

    /** {@code ON_SALE → CLOSED}. Terminal — selling is over for good. */
    public void close() {
        transitionTo(EventStatus.CLOSED);
    }

    /** {@code DRAFT}/{@code PUBLISHED}/{@code ON_SALE} {@code → CANCELLED}. Terminal. */
    public void cancel() {
        transitionTo(EventStatus.CANCELLED);
    }

    private void transitionTo(EventStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidEventStateException(
                    "Event cannot move from " + status + " to " + target);
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidEventDataException("Event " + fieldName + " must not be null");
        }
    }

    private static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventDataException("Event " + fieldName + " must not be blank");
        }
    }
}

package com.tienphat.domain.model;

import com.tienphat.domain.exception.InvalidEventDataException;
import com.tienphat.domain.exception.InvalidEventScheduleException;
import com.tienphat.domain.exception.InvalidEventStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();

    private static final Instant SALE_START = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Instant SALE_END = SALE_START.plus(7, ChronoUnit.DAYS);
    private static final Instant START = SALE_END.plus(1, ChronoUnit.DAYS);
    private static final Instant END = START.plus(3, ChronoUnit.HOURS);

    private static Event aDraft() {
        return Event.create(ID, ORGANIZER_ID, "Concert", "A concert", "My Dinh Stadium",
                START, END, SALE_START, SALE_END);
    }

    private static Event anEventIn(EventStatus status) {
        Event event = aDraft();
        switch (status) {
            case DRAFT -> { }
            case PUBLISHED -> event.publish();
            case ON_SALE -> {
                event.publish();
                event.startSale();
            }
            case CLOSED -> {
                event.publish();
                event.startSale();
                event.close();
            }
            case CANCELLED -> event.cancel();
        }
        return event;
    }

    @Test
    @DisplayName("create() returns a DRAFT event carrying the given fields, with createdAt == updatedAt")
    void create_succeeds() {
        Event event = aDraft();

        assertThat(event.getId()).isEqualTo(ID);
        assertThat(event.getOrganizerId()).isEqualTo(ORGANIZER_ID);
        assertThat(event.getName()).isEqualTo("Concert");
        assertThat(event.getVenueName()).isEqualTo("My Dinh Stadium");
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getCreatedAt()).isEqualTo(event.getUpdatedAt());
    }

    @Test
    @DisplayName("create() accepts a null description — the column is optional")
    void create_acceptsNullDescription() {
        Event event = Event.create(ID, ORGANIZER_ID, "Concert", null, "My Dinh Stadium",
                START, END, SALE_START, SALE_END);

        assertThat(event.getDescription()).isNull();
    }

    @Test
    @DisplayName("create() throws when startTime is not before endTime")
    void create_throwsOnInvertedEventWindow() {
        assertThatThrownBy(() -> Event.create(ID, ORGANIZER_ID, "Concert", null, "My Dinh Stadium",
                END, START, SALE_START, SALE_END))
                .isInstanceOf(InvalidEventScheduleException.class)
                .hasMessageContaining("startTime");
    }

    @Test
    @DisplayName("create() throws when startTime equals endTime — a zero-length event is not valid")
    void create_throwsOnZeroLengthEventWindow() {
        assertThatThrownBy(() -> Event.create(ID, ORGANIZER_ID, "Concert", null, "My Dinh Stadium",
                START, START, SALE_START, SALE_END))
                .isInstanceOf(InvalidEventScheduleException.class);
    }

    @Test
    @DisplayName("create() throws when saleStartTime is not before saleEndTime")
    void create_throwsOnInvertedSaleWindow() {
        assertThatThrownBy(() -> Event.create(ID, ORGANIZER_ID, "Concert", null, "My Dinh Stadium",
                START, END, SALE_END, SALE_START))
                .isInstanceOf(InvalidEventScheduleException.class)
                .hasMessageContaining("saleStartTime");
    }

    @Test
    @DisplayName("create() rejects a null id and a blank name")
    void create_throwsOnMissingRequiredFields() {
        assertThatThrownBy(() -> Event.create(null, ORGANIZER_ID, "Concert", null, "My Dinh Stadium",
                START, END, SALE_START, SALE_END))
                .isInstanceOf(InvalidEventDataException.class)
                .hasMessageContaining("id");

        assertThatThrownBy(() -> Event.create(ID, ORGANIZER_ID, "  ", null, "My Dinh Stadium",
                START, END, SALE_START, SALE_END))
                .isInstanceOf(InvalidEventDataException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("create() allows a sale window that runs past the event start — not an invariant")
    void create_allowsSaleOverlappingEventStart() {
        Event event = Event.create(ID, ORGANIZER_ID, "Concert", null, "My Dinh Stadium",
                START, END, SALE_START, END);

        assertThat(event.getSaleEndTime()).isEqualTo(END);
    }

    @Test
    @DisplayName("publish() moves DRAFT to PUBLISHED and bumps updatedAt")
    void publish_succeedsFromDraft() {
        Event event = aDraft();

        event.publish();

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getUpdatedAt()).isAfterOrEqualTo(event.getCreatedAt());
    }

    @Test
    @DisplayName("publish() throws from ON_SALE")
    void publish_throwsFromOnSale() {
        Event event = anEventIn(EventStatus.ON_SALE);

        assertThatThrownBy(event::publish)
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("ON_SALE");
    }

    @Test
    @DisplayName("startSale() succeeds from PUBLISHED and throws from DRAFT")
    void startSale_requiresPublished() {
        Event published = anEventIn(EventStatus.PUBLISHED);
        published.startSale();
        assertThat(published.getStatus()).isEqualTo(EventStatus.ON_SALE);

        assertThatThrownBy(aDraft()::startSale)
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    @DisplayName("close() succeeds from ON_SALE and throws from PUBLISHED")
    void close_requiresOnSale() {
        Event onSale = anEventIn(EventStatus.ON_SALE);
        onSale.close();
        assertThat(onSale.getStatus()).isEqualTo(EventStatus.CLOSED);

        assertThatThrownBy(anEventIn(EventStatus.PUBLISHED)::close)
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    @DisplayName("cancel() succeeds from DRAFT, PUBLISHED and ON_SALE")
    void cancel_succeedsFromEveryNonTerminalStatus() {
        for (EventStatus from : EnumSet.of(EventStatus.DRAFT, EventStatus.PUBLISHED, EventStatus.ON_SALE)) {
            Event event = anEventIn(from);

            event.cancel();

            assertThat(event.getStatus()).as("cancel from %s", from).isEqualTo(EventStatus.CANCELLED);
        }
    }

    @Test
    @DisplayName("cancel() throws from CLOSED — a finished event cannot be called off")
    void cancel_throwsFromClosed() {
        assertThatThrownBy(anEventIn(EventStatus.CLOSED)::cancel)
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    @DisplayName("a terminal event rejects every further transition")
    void terminalStatuses_rejectEveryMutator() {
        for (EventStatus terminal : EnumSet.of(EventStatus.CLOSED, EventStatus.CANCELLED)) {
            Event event = anEventIn(terminal);

            assertThatThrownBy(event::publish).isInstanceOf(InvalidEventStateException.class);
            assertThatThrownBy(event::startSale).isInstanceOf(InvalidEventStateException.class);
            assertThatThrownBy(event::close).isInstanceOf(InvalidEventStateException.class);
            assertThatThrownBy(event::cancel).isInstanceOf(InvalidEventStateException.class);
            assertThat(event.getStatus()).as("%s is unchanged", terminal).isEqualTo(terminal);
        }
    }

    @Test
    @DisplayName("every EventStatus is reachable through a public mutator — no dead status")
    void everyStatus_isReachable() {
        Set<EventStatus> reached = EnumSet.noneOf(EventStatus.class);

        for (EventStatus status : EventStatus.values()) {
            reached.add(anEventIn(status).getStatus());
        }

        assertThat(reached).containsExactlyInAnyOrder(EventStatus.values());
    }

    @Test
    @DisplayName("a rejected transition leaves the status untouched")
    void rejectedTransition_doesNotMutate() {
        Event event = aDraft();

        assertThatThrownBy(event::close).isInstanceOf(InvalidEventStateException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    @DisplayName("reconstitute() lands directly in a terminal status without replaying transitions")
    void reconstitute_preservesStoredState() {
        Instant createdAt = Instant.parse("2026-02-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-04-20T16:00:00Z");

        Event event = Event.reconstitute(ID, ORGANIZER_ID, "Concert", null, "My Dinh Stadium",
                START, END, SALE_START, SALE_END, EventStatus.CANCELLED, createdAt, updatedAt);

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(event.getCreatedAt()).as("create() would force now()").isEqualTo(createdAt);
        assertThat(event.getUpdatedAt()).isEqualTo(updatedAt);
        assertThatThrownBy(event::publish)
                .as("still terminal — guards apply to a reconstituted entity")
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    @DisplayName("reconstitute() rejects a null in a required column")
    void reconstitute_rejectsNullRequiredField() {
        assertThatThrownBy(() -> Event.reconstitute(ID, ORGANIZER_ID, "Concert", null, "My Dinh Stadium",
                START, END, SALE_START, SALE_END, null, Instant.now(), Instant.now()))
                .isInstanceOf(InvalidEventDataException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("updateDetails() applies every field from DRAFT and bumps updatedAt")
    void updateDetails_succeedsFromDraft() {
        Event event = aDraft();
        Instant newStart = START.plus(1, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(3, ChronoUnit.HOURS);
        Instant newSaleStart = SALE_START.plus(1, ChronoUnit.DAYS);
        Instant newSaleEnd = newSaleStart.plus(7, ChronoUnit.DAYS);

        event.updateDetails("New name", "New description", "New Venue",
                newStart, newEnd, newSaleStart, newSaleEnd);

        assertThat(event.getName()).isEqualTo("New name");
        assertThat(event.getDescription()).isEqualTo("New description");
        assertThat(event.getVenueName()).isEqualTo("New Venue");
        assertThat(event.getStartTime()).isEqualTo(newStart);
        assertThat(event.getEndTime()).isEqualTo(newEnd);
        assertThat(event.getSaleStartTime()).isEqualTo(newSaleStart);
        assertThat(event.getSaleEndTime()).isEqualTo(newSaleEnd);
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getUpdatedAt()).isAfterOrEqualTo(event.getCreatedAt());
    }

    @Test
    @DisplayName("updateDetails() throws from every non-DRAFT status and leaves the entity unchanged")
    void updateDetails_throwsFromEveryNonDraftStatus() {
        for (EventStatus status : EnumSet.complementOf(EnumSet.of(EventStatus.DRAFT))) {
            Event event = anEventIn(status);

            assertThatThrownBy(() -> event.updateDetails("New name", "New description", "New Venue",
                    START, END, SALE_START, SALE_END))
                    .as("updateDetails from %s", status)
                    .isInstanceOf(InvalidEventStateException.class);

            assertThat(event.getName()).as("%s is unchanged", status).isEqualTo("Concert");
        }
    }

    @Test
    @DisplayName("updateDetails() rejects a blank name")
    void updateDetails_throwsOnBlankName() {
        Event event = aDraft();

        assertThatThrownBy(() -> event.updateDetails(" ", "New description", "New Venue",
                START, END, SALE_START, SALE_END))
                .isInstanceOf(InvalidEventDataException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("updateDetails() rejects an inverted event window")
    void updateDetails_throwsOnInvertedEventWindow() {
        Event event = aDraft();

        assertThatThrownBy(() -> event.updateDetails("New name", "New description", "New Venue",
                END, START, SALE_START, SALE_END))
                .isInstanceOf(InvalidEventScheduleException.class)
                .hasMessageContaining("startTime");
    }

    @Test
    @DisplayName("updateDetails() rejects an inverted sale window")
    void updateDetails_throwsOnInvertedSaleWindow() {
        Event event = aDraft();

        assertThatThrownBy(() -> event.updateDetails("New name", "New description", "New Venue",
                START, END, SALE_END, SALE_START))
                .isInstanceOf(InvalidEventScheduleException.class)
                .hasMessageContaining("saleStartTime");
    }

    @Test
    @DisplayName("equality is by id alone")
    void equality_isIdentityBased() {
        Event one = Event.create(ID, ORGANIZER_ID, "A", null, "V1", START, END, SALE_START, SALE_END);
        Event two = Event.create(ID, UUID.randomUUID(), "B", null, "V2", START, END, SALE_START, SALE_END);

        assertThat(one).isEqualTo(two);
        assertThat(one).hasSameHashCodeAs(two);
        assertThat(one).isNotEqualTo(
                Event.create(UUID.randomUUID(), ORGANIZER_ID, "A", null, "V1", START, END, SALE_START, SALE_END));
    }
}

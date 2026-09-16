package com.tienphat.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventStatusTest {

    /**
     * The transition table restated independently of the enum. If the two ever disagree, the
     * change was made in only one place — which is exactly what this test is for.
     */
    private static final Map<EventStatus, Set<EventStatus>> EXPECTED = Map.of(
            EventStatus.DRAFT, Set.of(EventStatus.PUBLISHED, EventStatus.CANCELLED),
            EventStatus.PUBLISHED, Set.of(EventStatus.ON_SALE, EventStatus.CANCELLED),
            EventStatus.ON_SALE, Set.of(EventStatus.CLOSED, EventStatus.CANCELLED),
            EventStatus.CLOSED, Set.of(),
            EventStatus.CANCELLED, Set.of());

    @Test
    @DisplayName("canTransitionTo() matches the table for all 25 (from, to) pairs")
    void transitionMatrix_isExhaustivelyCorrect() {
        for (EventStatus from : EventStatus.values()) {
            for (EventStatus to : EventStatus.values()) {
                boolean allowed = EXPECTED.get(from).contains(to);

                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(allowed);
            }
        }
    }

    @Test
    @DisplayName("the expected table covers every enum constant — a new status cannot slip in untested")
    void expectedTable_coversEveryStatus() {
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrder(EventStatus.values());
    }

    @Test
    @DisplayName("no status can transition to itself")
    void noSelfTransitions() {
        for (EventStatus status : EventStatus.values()) {
            assertThat(status.canTransitionTo(status)).as("%s -> itself", status).isFalse();
        }
    }

    @Test
    @DisplayName("canTransitionTo(null) is false rather than throwing")
    void nullTarget_isRejected() {
        assertThat(EventStatus.DRAFT.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("CLOSED and CANCELLED are terminal")
    void terminalStatuses_haveNoOutgoingEdges() {
        for (EventStatus to : EventStatus.values()) {
            assertThat(EventStatus.CLOSED.canTransitionTo(to)).isFalse();
            assertThat(EventStatus.CANCELLED.canTransitionTo(to)).isFalse();
        }
    }
}

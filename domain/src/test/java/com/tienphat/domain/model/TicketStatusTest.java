package com.tienphat.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TicketStatusTest {

    /** Restated independently of the enum — see {@code EventStatusTest} for why. */
    private static final Map<TicketStatus, Set<TicketStatus>> EXPECTED = Map.of(
            TicketStatus.ISSUED, Set.of(TicketStatus.CHECKED_IN, TicketStatus.CANCELLED),
            TicketStatus.CHECKED_IN, Set.of(),
            TicketStatus.CANCELLED, Set.of());

    @Test
    @DisplayName("canTransitionTo() matches the table for all 9 (from, to) pairs")
    void transitionMatrix_isExhaustivelyCorrect() {
        for (TicketStatus from : TicketStatus.values()) {
            for (TicketStatus to : TicketStatus.values()) {
                boolean allowed = EXPECTED.get(from).contains(to);

                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(allowed);
            }
        }
    }

    @Test
    @DisplayName("the expected table covers every enum constant")
    void expectedTable_coversEveryStatus() {
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrder(TicketStatus.values());
    }

    @Test
    @DisplayName("an admitted ticket can never go back to ISSUED — the QR must not pass twice")
    void checkedIn_isIrreversible() {
        assertThat(TicketStatus.CHECKED_IN.canTransitionTo(TicketStatus.ISSUED)).isFalse();
        assertThat(TicketStatus.CANCELLED.canTransitionTo(TicketStatus.ISSUED)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo(null) is false rather than throwing")
    void nullTarget_isRejected() {
        assertThat(TicketStatus.ISSUED.canTransitionTo(null)).isFalse();
    }
}

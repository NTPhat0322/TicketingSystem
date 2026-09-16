package com.tienphat.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTypeStatusTest {

    /** Restated independently of the enum — see {@code EventStatusTest} for why. */
    private static final Map<TicketTypeStatus, Set<TicketTypeStatus>> EXPECTED = Map.of(
            TicketTypeStatus.ACTIVE, Set.of(TicketTypeStatus.SOLD_OUT, TicketTypeStatus.CLOSED),
            TicketTypeStatus.SOLD_OUT, Set.of(TicketTypeStatus.ACTIVE, TicketTypeStatus.CLOSED),
            TicketTypeStatus.CLOSED, Set.of());

    @Test
    @DisplayName("canTransitionTo() matches the table for all 9 (from, to) pairs")
    void transitionMatrix_isExhaustivelyCorrect() {
        for (TicketTypeStatus from : TicketTypeStatus.values()) {
            for (TicketTypeStatus to : TicketTypeStatus.values()) {
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
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrder(TicketTypeStatus.values());
    }

    @Test
    @DisplayName("canTransitionTo(null) is false rather than throwing")
    void nullTarget_isRejected() {
        assertThat(TicketTypeStatus.ACTIVE.canTransitionTo(null)).isFalse();
    }
}

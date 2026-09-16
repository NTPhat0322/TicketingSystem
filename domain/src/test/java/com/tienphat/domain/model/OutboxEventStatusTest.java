package com.tienphat.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventStatusTest {

    /** Restated independently of the enum — see {@code EventStatusTest} for why. */
    private static final Map<OutboxEventStatus, Set<OutboxEventStatus>> EXPECTED = Map.of(
            OutboxEventStatus.PENDING, Set.of(OutboxEventStatus.PUBLISHED, OutboxEventStatus.FAILED),
            OutboxEventStatus.PUBLISHED, Set.of(),
            OutboxEventStatus.FAILED, Set.of(OutboxEventStatus.PENDING));

    @Test
    @DisplayName("canTransitionTo() matches the table for all 9 (from, to) pairs")
    void transitionMatrix_isExhaustivelyCorrect() {
        for (OutboxEventStatus from : OutboxEventStatus.values()) {
            for (OutboxEventStatus to : OutboxEventStatus.values()) {
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
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrder(OutboxEventStatus.values());
    }

    @Test
    @DisplayName("FAILED reopens to PENDING — the only backward edge in this domain")
    void failed_reopensForRetry() {
        assertThat(OutboxEventStatus.FAILED.canTransitionTo(OutboxEventStatus.PENDING)).isTrue();
    }

    @Test
    @DisplayName("PUBLISHED is terminal — a delivered message is never re-queued by the domain")
    void published_isTerminal() {
        assertThat(OutboxEventStatus.PUBLISHED.canTransitionTo(OutboxEventStatus.PENDING)).isFalse();
        assertThat(OutboxEventStatus.PUBLISHED.canTransitionTo(OutboxEventStatus.FAILED)).isFalse();
    }

    @Test
    @DisplayName("FAILED cannot jump straight to PUBLISHED without going through PENDING")
    void failed_cannotPublishDirectly() {
        assertThat(OutboxEventStatus.FAILED.canTransitionTo(OutboxEventStatus.PUBLISHED)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo(null) is false rather than throwing")
    void nullTarget_isRejected() {
        assertThat(OutboxEventStatus.PENDING.canTransitionTo(null)).isFalse();
    }
}

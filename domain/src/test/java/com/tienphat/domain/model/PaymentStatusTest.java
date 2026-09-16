package com.tienphat.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    /** Restated independently of the enum — see {@code EventStatusTest} for why. */
    private static final Map<PaymentStatus, Set<PaymentStatus>> EXPECTED = Map.of(
            PaymentStatus.PENDING, Set.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED),
            PaymentStatus.SUCCESS, Set.of(),
            PaymentStatus.FAILED, Set.of());

    @Test
    @DisplayName("canTransitionTo() matches the table for all 9 (from, to) pairs")
    void transitionMatrix_isExhaustivelyCorrect() {
        for (PaymentStatus from : PaymentStatus.values()) {
            for (PaymentStatus to : PaymentStatus.values()) {
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
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrder(PaymentStatus.values());
    }

    @Test
    @DisplayName("FAILED is terminal — a retry is a new attempt with a new transactionRef")
    void failed_hasNoRetryEdge() {
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.SUCCESS)).isFalse();
    }

    @Test
    @DisplayName("a settled payment can never go back to PENDING")
    void settledStatuses_cannotReopen() {
        assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo(null) is false rather than throwing")
    void nullTarget_isRejected() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(null)).isFalse();
    }
}

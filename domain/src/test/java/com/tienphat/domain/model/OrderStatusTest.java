package com.tienphat.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    /** Restated independently of the enum — see {@code EventStatusTest} for why. */
    private static final Map<OrderStatus, Set<OrderStatus>> EXPECTED = Map.of(
            OrderStatus.PENDING_PAYMENT,
            Set.of(OrderStatus.PAID, OrderStatus.EXPIRED, OrderStatus.CANCELLED, OrderStatus.FAILED),
            OrderStatus.PAID, Set.of(),
            OrderStatus.EXPIRED, Set.of(),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.FAILED, Set.of());

    @Test
    @DisplayName("canTransitionTo() matches the table for all 25 (from, to) pairs")
    void transitionMatrix_isExhaustivelyCorrect() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
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
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrder(OrderStatus.values());
    }

    @Test
    @DisplayName("PENDING_PAYMENT is the only non-terminal status — an order settles exactly once")
    void everySettledStatus_isTerminal() {
        for (OrderStatus status : OrderStatus.values()) {
            boolean hasOutgoing = EXPECTED.get(status).stream().anyMatch(status::canTransitionTo);

            assertThat(hasOutgoing)
                    .as("%s has outgoing transitions", status)
                    .isEqualTo(status == OrderStatus.PENDING_PAYMENT);
        }
    }

    @Test
    @DisplayName("no status can transition to itself, PENDING_PAYMENT included")
    void selfTransitions_areRejected() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(status)).as("%s -> itself", status).isFalse();
        }
    }

    @Test
    @DisplayName("canTransitionTo(null) is false rather than throwing")
    void nullTarget_isRejected() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(null)).isFalse();
    }
}

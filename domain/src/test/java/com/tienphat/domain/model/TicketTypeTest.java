package com.tienphat.domain.model;

import com.tienphat.domain.exception.InsufficientStockException;
import com.tienphat.domain.exception.InvalidReservationQuantityException;
import com.tienphat.domain.exception.InvalidTicketTypeDataException;
import com.tienphat.domain.exception.TicketTypeNotAvailableException;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTypeTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final Money PRICE = Money.of(new BigDecimal("500000"));
    private static final int TOTAL = 10;

    private static TicketType anActiveType() {
        return TicketType.create(ID, EVENT_ID, "VIP", PRICE, TOTAL, 4, 600);
    }

    private static TicketType aSoldOutType() {
        TicketType type = anActiveType();
        type.confirmSale(TOTAL);
        return type;
    }

    /**
     * A stored row that violates the class invariant: {@code SOLD_OUT} with capacity still left,
     * which is what a {@code total_quantity} raised outside the domain looks like on load.
     * {@code reconstitute} accepts it by design — it null-checks only.
     */
    private static TicketType aDivergentSoldOutType(int total, int sold) {
        return TicketType.reconstitute(ID, EVENT_ID, "VIP", PRICE, total, sold, 4, 600, 3,
                TicketTypeStatus.SOLD_OUT, STORED_CREATED_AT, STORED_UPDATED_AT);
    }

    private static TicketType aClosedType() {
        TicketType type = anActiveType();
        type.close();
        return type;
    }

    // ---- create() ----------------------------------------------------------------------------

    @Test
    @DisplayName("create() returns an ACTIVE type with nothing sold and version 0")
    void create_succeeds() {
        TicketType type = anActiveType();

        assertThat(type.getId()).isEqualTo(ID);
        assertThat(type.getEventId()).isEqualTo(EVENT_ID);
        assertThat(type.getPrice()).isEqualTo(PRICE);
        assertThat(type.getTotalQuantity()).isEqualTo(TOTAL);
        assertThat(type.getSoldQuantity()).isZero();
        assertThat(type.getMaxPerUser()).isEqualTo(4);
        assertThat(type.getHoldDurationSec()).isEqualTo(600);
        assertThat(type.getVersion()).isZero();
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.ACTIVE);
        assertThat(type.getCreatedAt()).isEqualTo(type.getUpdatedAt());
    }

    @Test
    @DisplayName("create() rejects a non-positive totalQuantity")
    void create_throwsOnNonPositiveTotalQuantity() {
        assertThatThrownBy(() -> TicketType.create(ID, EVENT_ID, "VIP", PRICE, 0, 4, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("totalQuantity");
        assertThatThrownBy(() -> TicketType.create(ID, EVENT_ID, "VIP", PRICE, -1, 4, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class);
    }

    @Test
    @DisplayName("create() rejects a non-positive maxPerUser or holdDurationSec")
    void create_throwsOnNonPositiveLimits() {
        assertThatThrownBy(() -> TicketType.create(ID, EVENT_ID, "VIP", PRICE, TOTAL, 0, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("maxPerUser");
        assertThatThrownBy(() -> TicketType.create(ID, EVENT_ID, "VIP", PRICE, TOTAL, 4, 0))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("holdDurationSec");
    }

    @Test
    @DisplayName("create() rejects null id, eventId, price and a blank name")
    void create_throwsOnMissingRequiredFields() {
        assertThatThrownBy(() -> TicketType.create(null, EVENT_ID, "VIP", PRICE, TOTAL, 4, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> TicketType.create(ID, null, "VIP", PRICE, TOTAL, 4, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class).hasMessageContaining("eventId");
        assertThatThrownBy(() -> TicketType.create(ID, EVENT_ID, " ", PRICE, TOTAL, 4, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> TicketType.create(ID, EVENT_ID, "VIP", null, TOTAL, 4, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class).hasMessageContaining("price");
    }

    @Test
    @DisplayName("create() accepts a zero price — a free tier is valid")
    void create_acceptsFreeTier() {
        TicketType type = TicketType.create(ID, EVENT_ID, "Free", Money.zero(), TOTAL, 4, 600);

        assertThat(type.getPrice().isZero()).isTrue();
    }

    // ---- confirmSale() -----------------------------------------------------------------------

    @Test
    @DisplayName("confirmSale() increments soldQuantity and stays ACTIVE below capacity")
    void confirmSale_succeeds() {
        TicketType type = anActiveType();

        type.confirmSale(3);

        assertThat(type.getSoldQuantity()).isEqualTo(3);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.ACTIVE);
        assertThat(type.getUpdatedAt()).isAfterOrEqualTo(type.getCreatedAt());
    }

    @Test
    @DisplayName("confirmSale() rejects a non-positive quantity")
    void confirmSale_throwsOnNonPositiveQuantity() {
        TicketType type = anActiveType();

        assertThatThrownBy(() -> type.confirmSale(0)).isInstanceOf(InvalidReservationQuantityException.class);
        assertThatThrownBy(() -> type.confirmSale(-2)).isInstanceOf(InvalidReservationQuantityException.class);
        assertThat(type.getSoldQuantity()).isZero();
    }

    @Test
    @DisplayName("confirmSale() throws InsufficientStockException when qty exceeds remaining capacity")
    void confirmSale_throwsWhenOverCapacity() {
        TicketType type = anActiveType();
        type.confirmSale(8);

        assertThatThrownBy(() -> type.confirmSale(3))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("2 remaining");
        assertThat(type.getSoldQuantity()).as("rejected sale leaves the counter untouched").isEqualTo(8);
    }

    @Test
    @DisplayName("confirmSale() flips to SOLD_OUT exactly when capacity is reached")
    void confirmSale_transitionsToSoldOutAtCapacity() {
        TicketType type = anActiveType();

        type.confirmSale(TOTAL - 1);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.ACTIVE);

        type.confirmSale(1);
        assertThat(type.getSoldQuantity()).isEqualTo(TOTAL);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("confirmSale() throws TicketTypeNotAvailableException on a CLOSED type")
    void confirmSale_throwsWhenClosed() {
        TicketType type = aClosedType();

        assertThatThrownBy(() -> type.confirmSale(1))
                .isInstanceOf(TicketTypeNotAvailableException.class);
        assertThat(type.getSoldQuantity()).isZero();
    }

    /**
     * Pins the guard ordering. Both a {@code status == CLOSED} and a {@code status != ACTIVE} guard
     * reject this call; only the exception type tells them apart. Changing the guard to
     * {@code != ACTIVE} flips this to {@code TicketTypeNotAvailableException} and hides a
     * Redis/Postgres divergence — this test is what catches that edit.
     */
    @Test
    @DisplayName("confirmSale() on SOLD_OUT throws InsufficientStockException, NOT TicketTypeNotAvailableException")
    void confirmSale_onSoldOutSurfacesDivergenceSignal() {
        TicketType type = aSoldOutType();

        assertThatThrownBy(() -> type.confirmSale(1))
                .isExactlyInstanceOf(InsufficientStockException.class);
    }

    /**
     * The row above is consistent, so the capacity guard stops it before the transition. This one is
     * not: {@code SOLD_OUT} with 50 left. Before the assignment moved below the transition, this
     * incremented the counter and then threw {@code SOLD_OUT -> SOLD_OUT}, handing the caller a
     * failure over an already-mutated aggregate. Now it completes.
     */
    @Test
    @DisplayName("confirmSale() completes on a SOLD_OUT row that still has capacity, restoring the invariant")
    void confirmSale_onDivergentSoldOutRowCompletes() {
        TicketType type = aDivergentSoldOutType(150, 100);

        type.confirmSale(50);

        assertThat(type.getSoldQuantity()).isEqualTo(150);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("confirmSale() below capacity on a divergent row moves the counter and leaves the status alone")
    void confirmSale_onDivergentSoldOutRowBelowCapacityKeepsStatus() {
        TicketType type = aDivergentSoldOutType(150, 100);

        type.confirmSale(20);

        assertThat(type.getSoldQuantity()).isEqualTo(120);
        assertThat(type.getStatus())
                .as("still divergent, but no worse — repairing the status downward is not this method's call")
                .isEqualTo(TicketTypeStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("a rejected confirmSale() leaves soldQuantity untouched")
    void confirmSale_leavesCounterUntouchedWhenRejected() {
        TicketType type = aDivergentSoldOutType(150, 100);

        assertThatThrownBy(() -> type.confirmSale(51))
                .isExactlyInstanceOf(InsufficientStockException.class);

        assertThat(type.getSoldQuantity()).isEqualTo(100);
    }

    // ---- releaseSale() -----------------------------------------------------------------------

    @Test
    @DisplayName("releaseSale() decrements soldQuantity and stays ACTIVE")
    void releaseSale_succeeds() {
        TicketType type = anActiveType();
        type.confirmSale(5);

        type.releaseSale(2);

        assertThat(type.getSoldQuantity()).isEqualTo(3);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.ACTIVE);
    }

    @Test
    @DisplayName("releaseSale() on SOLD_OUT reopens capacity and flips back to ACTIVE")
    void releaseSale_reopensSoldOut() {
        TicketType type = aSoldOutType();

        type.releaseSale(1);

        assertThat(type.getSoldQuantity()).isEqualTo(TOTAL - 1);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.ACTIVE);
    }

    @Test
    @DisplayName("invariant: SOLD_OUT exactly at capacity, ACTIVE again once capacity reopens")
    void invariant_soldOutIffAtCapacity() {
        TicketType type = anActiveType();

        type.confirmSale(TOTAL);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.SOLD_OUT);
        assertThat(type.getSoldQuantity()).isEqualTo(type.getTotalQuantity());

        type.releaseSale(1);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.ACTIVE);
        assertThat(type.getSoldQuantity()).isLessThan(type.getTotalQuantity());

        type.confirmSale(1);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("releaseSale() on a divergent SOLD_OUT row decrements and reopens to ACTIVE")
    void releaseSale_onDivergentSoldOutRowReopens() {
        TicketType type = aDivergentSoldOutType(150, 100);

        type.releaseSale(10);

        assertThat(type.getSoldQuantity()).isEqualTo(90);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.ACTIVE);
    }

    @Test
    @DisplayName("releaseSale() rejects a quantity that would drive soldQuantity negative")
    void releaseSale_throwsWhenBelowZero() {
        TicketType type = anActiveType();
        type.confirmSale(2);

        assertThatThrownBy(() -> type.releaseSale(3))
                .isInstanceOf(InvalidReservationQuantityException.class);
        assertThat(type.getSoldQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("releaseSale() rejects a non-positive quantity")
    void releaseSale_throwsOnNonPositiveQuantity() {
        TicketType type = anActiveType();
        type.confirmSale(2);

        assertThatThrownBy(() -> type.releaseSale(0)).isInstanceOf(InvalidReservationQuantityException.class);
        assertThat(type.getSoldQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("releaseSale() on a CLOSED type decrements the counter and stays CLOSED — refunds outlive sales")
    void releaseSale_onClosedStaysClosed() {
        TicketType type = aSoldOutType();
        type.close();

        type.releaseSale(4);

        assertThat(type.getSoldQuantity()).isEqualTo(TOTAL - 4);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.CLOSED);
    }

    // ---- close() -----------------------------------------------------------------------------

    @Test
    @DisplayName("close() succeeds from ACTIVE and from SOLD_OUT")
    void close_succeedsFromActiveAndSoldOut() {
        TicketType active = anActiveType();
        active.close();
        assertThat(active.getStatus()).isEqualTo(TicketTypeStatus.CLOSED);

        TicketType soldOut = aSoldOutType();
        soldOut.close();
        assertThat(soldOut.getStatus()).isEqualTo(TicketTypeStatus.CLOSED);
        assertThat(soldOut.getSoldQuantity()).as("closing does not touch the counter").isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("close() throws TicketTypeNotAvailableException when already CLOSED")
    void close_throwsWhenAlreadyClosed() {
        TicketType type = aClosedType();

        assertThatThrownBy(type::close).isInstanceOf(TicketTypeNotAvailableException.class);
    }

    // ---- updateDetails() ----------------------------------------------------------------------

    @Test
    @DisplayName("updateDetails() applies every field and keeps ACTIVE status when capacity is not exhausted")
    void updateDetails_succeedsAndKeepsStatus() {
        TicketType type = anActiveType();
        Money newPrice = Money.of(new BigDecimal("750000"));

        type.updateDetails("VVIP", newPrice, 20, 6, 900);

        assertThat(type.getName()).isEqualTo("VVIP");
        assertThat(type.getPrice()).isEqualTo(newPrice);
        assertThat(type.getTotalQuantity()).isEqualTo(20);
        assertThat(type.getMaxPerUser()).isEqualTo(6);
        assertThat(type.getHoldDurationSec()).isEqualTo(900);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.ACTIVE);
        assertThat(type.getSoldQuantity()).isZero();
        assertThat(type.getVersion()).isZero();
        assertThat(type.getUpdatedAt()).isAfterOrEqualTo(type.getCreatedAt());
    }

    @Test
    @DisplayName("updateDetails() rejects a non-positive totalQuantity, maxPerUser or holdDurationSec")
    void updateDetails_throwsOnNonPositiveFields() {
        TicketType type = anActiveType();

        assertThatThrownBy(() -> type.updateDetails("VIP", PRICE, 0, 4, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("totalQuantity");
        assertThatThrownBy(() -> type.updateDetails("VIP", PRICE, TOTAL, 0, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("maxPerUser");
        assertThatThrownBy(() -> type.updateDetails("VIP", PRICE, TOTAL, 4, 0))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("holdDurationSec");
    }

    @Test
    @DisplayName("updateDetails() rejects a blank name and a null price")
    void updateDetails_throwsOnMissingRequiredFields() {
        TicketType type = anActiveType();

        assertThatThrownBy(() -> type.updateDetails(" ", PRICE, TOTAL, 4, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> type.updateDetails("VIP", null, TOTAL, 4, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("price");
    }

    @Test
    @DisplayName("updateDetails() rejects a totalQuantity below soldQuantity")
    void updateDetails_throwsWhenTotalQuantityBelowSoldQuantity() {
        TicketType type = anActiveType();
        type.confirmSale(5);

        assertThatThrownBy(() -> type.updateDetails("VIP", PRICE, 4, 4, 600))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("totalQuantity");
        assertThat(type.getSoldQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("updateDetails() throws TicketTypeNotAvailableException when CLOSED")
    void updateDetails_throwsWhenClosed() {
        TicketType type = aClosedType();

        assertThatThrownBy(() -> type.updateDetails("VVIP", PRICE, TOTAL, 4, 600))
                .isInstanceOf(TicketTypeNotAvailableException.class);
        assertThat(type.getName()).isEqualTo("VIP");
    }

    @Test
    @DisplayName("updateDetails() transitions ACTIVE to SOLD_OUT when the new totalQuantity matches soldQuantity")
    void updateDetails_transitionsActiveToSoldOut() {
        TicketType type = anActiveType();
        type.confirmSale(4);

        type.updateDetails("VIP", PRICE, 4, 4, 600);

        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.SOLD_OUT);
        assertThat(type.getSoldQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("updateDetails() transitions SOLD_OUT back to ACTIVE when the new totalQuantity exceeds soldQuantity")
    void updateDetails_transitionsSoldOutToActive() {
        TicketType type = aSoldOutType();

        type.updateDetails("VIP", PRICE, TOTAL + 5, 4, 600);

        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.ACTIVE);
        assertThat(type.getSoldQuantity()).isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("updateDetails() never changes soldQuantity or version")
    void updateDetails_neverChangesSoldQuantityOrVersion() {
        TicketType type = aDivergentSoldOutType(150, 100);

        type.updateDetails("VIP", PRICE, 200, 4, 600);

        assertThat(type.getSoldQuantity()).isEqualTo(100);
        assertThat(type.getVersion()).isEqualTo(3);
    }

    // ---- reconstitute() ----------------------------------------------------------------------

    private static final Instant STORED_CREATED_AT = Instant.parse("2026-07-01T09:00:00Z");
    private static final Instant STORED_UPDATED_AT = Instant.parse("2026-08-15T17:30:00Z");

    /** The whole point of the method: state that {@code create()} would reset survives a load. */
    @Test
    @DisplayName("reconstitute() restores stored state create() would have forced back to defaults")
    void reconstitute_preservesStoredState() {
        TicketType type = TicketType.reconstitute(ID, EVENT_ID, "VIP", PRICE, 1000, 1000, 4, 600, 7,
                TicketTypeStatus.SOLD_OUT, STORED_CREATED_AT, STORED_UPDATED_AT);

        assertThat(type.getSoldQuantity()).as("create() would force 0").isEqualTo(1000);
        assertThat(type.getVersion()).as("create() would force 0").isEqualTo(7);
        assertThat(type.getStatus()).as("create() would force ACTIVE").isEqualTo(TicketTypeStatus.SOLD_OUT);
        assertThat(type.getCreatedAt()).as("create() would force now()").isEqualTo(STORED_CREATED_AT);
        assertThat(type.getUpdatedAt()).isEqualTo(STORED_UPDATED_AT);
        assertThat(type.getTotalQuantity()).isEqualTo(1000);
        assertThat(type.getMaxPerUser()).isEqualTo(4);
        assertThat(type.getHoldDurationSec()).isEqualTo(600);
    }

    @Test
    @DisplayName("a reconstituted entity is fully functional — guarded mutators still work on it")
    void reconstitute_yieldsAWorkingEntity() {
        TicketType type = TicketType.reconstitute(ID, EVENT_ID, "VIP", PRICE, 1000, 999, 4, 600, 7,
                TicketTypeStatus.ACTIVE, STORED_CREATED_AT, STORED_UPDATED_AT);

        type.confirmSale(1);

        assertThat(type.getSoldQuantity()).isEqualTo(1000);
        assertThat(type.getStatus()).isEqualTo(TicketTypeStatus.SOLD_OUT);
        assertThatThrownBy(() -> type.confirmSale(1)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("reconstitute() rejects a null in a required column — that row is corrupt")
    void reconstitute_rejectsNullRequiredField() {
        assertThatThrownBy(() -> TicketType.reconstitute(ID, EVENT_ID, "VIP", PRICE, 10, 0, 4, 600, 0,
                null, STORED_CREATED_AT, STORED_UPDATED_AT))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("status");

        assertThatThrownBy(() -> TicketType.reconstitute(ID, EVENT_ID, "VIP", PRICE, 10, 0, 4, 600, 0,
                TicketTypeStatus.ACTIVE, null, STORED_UPDATED_AT))
                .isInstanceOf(InvalidTicketTypeDataException.class)
                .hasMessageContaining("createdAt");
    }

    // ---- structural --------------------------------------------------------------------------

    @Test
    @DisplayName("every TicketTypeStatus is reachable through a public method — no dead status")
    void everyStatus_isReachable() {
        Set<TicketTypeStatus> reached = EnumSet.of(
                anActiveType().getStatus(),
                aSoldOutType().getStatus(),
                aClosedType().getStatus());

        assertThat(reached).containsExactlyInAnyOrder(TicketTypeStatus.values());
    }

    /**
     * Design doc §2.4: holds go through {@code StockCachePort} only. Reads method names, never
     * touches state — so this does not break the no-reflection-to-build-state rule.
     */
    @Test
    @DisplayName("TicketType declares no reserve-style method — holds live behind StockCachePort")
    void noReserveMethodOnEntity() {
        assertThat(Arrays.stream(TicketType.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.toLowerCase().contains("reserve"));
    }

    @Test
    @DisplayName("equality is by id alone")
    void equality_isIdentityBased() {
        TicketType one = TicketType.create(ID, EVENT_ID, "VIP", PRICE, 10, 4, 600);
        TicketType two = TicketType.create(ID, UUID.randomUUID(), "Standard", Money.zero(), 99, 1, 60);

        assertThat(one).isEqualTo(two);
        assertThat(one).hasSameHashCodeAs(two);
        assertThat(one).isNotEqualTo(TicketType.create(UUID.randomUUID(), EVENT_ID, "VIP", PRICE, 10, 4, 600));
    }
}

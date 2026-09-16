package com.tienphat.domain.model;

import com.tienphat.domain.exception.DomainException;
import com.tienphat.domain.exception.DuplicatePaymentException;
import com.tienphat.domain.exception.InvalidOrderDataException;
import com.tienphat.domain.exception.InvalidOrderItemException;
import com.tienphat.domain.exception.InvalidOrderStateException;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final Money PRICE = Money.of(new BigDecimal("150000"));
    private static final int HOLD_SEC = 300;

    private static Order aPendingOrder() {
        return Order.create("ORD-0001", USER_ID, EVENT_ID, HOLD_SEC);
    }

    private static Order anOrderIn(OrderStatus status) {
        Order order = aPendingOrder();
        switch (status) {
            case PENDING_PAYMENT -> { }
            case PAID -> order.pay();
            case EXPIRED -> order.expire();
            case CANCELLED -> order.cancel();
            case FAILED -> order.fail();
        }
        return order;
    }

    @Test
    @DisplayName("create() opens an empty PENDING_PAYMENT order with a zero total")
    void create_succeeds() {
        Order order = aPendingOrder();

        assertThat(order.getOrderCode()).isEqualTo("ORD-0001");
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getEventId()).isEqualTo(EVENT_ID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTotalAmount().isZero()).isTrue();
        assertThat(order.getItems()).isEmpty();
        assertThat(order.getPaidAt()).as("nothing paid yet").isNull();
        assertThat(order.getCreatedAt()).isEqualTo(order.getUpdatedAt());
    }

    @Test
    @DisplayName("create() mints a UUIDv7 id — no database round trip needed for identity")
    void create_generatesUuidV7Id() {
        Order one = aPendingOrder();
        Order two = aPendingOrder();

        assertThat(one.getId().version()).isEqualTo(7);
        assertThat(one.getId()).isNotEqualTo(two.getId());
    }

    @Test
    @DisplayName("create() sets expiresAt to reservedAt + holdDurationSec")
    void create_derivesExpiryFromHoldDuration() {
        Order order = aPendingOrder();

        assertThat(order.getExpiresAt()).isEqualTo(order.getReservedAt().plusSeconds(HOLD_SEC));
    }

    @Test
    @DisplayName("create() rejects a blank orderCode, null ids and a non-positive hold")
    void create_rejectsInvalidData() {
        assertThatThrownBy(() -> Order.create("  ", USER_ID, EVENT_ID, HOLD_SEC))
                .isInstanceOf(InvalidOrderDataException.class)
                .hasMessageContaining("orderCode");

        assertThatThrownBy(() -> Order.create("ORD-0001", null, EVENT_ID, HOLD_SEC))
                .isInstanceOf(InvalidOrderDataException.class)
                .hasMessageContaining("userId");

        assertThatThrownBy(() -> Order.create("ORD-0001", USER_ID, null, HOLD_SEC))
                .isInstanceOf(InvalidOrderDataException.class)
                .hasMessageContaining("eventId");

        assertThatThrownBy(() -> Order.create("ORD-0001", USER_ID, EVENT_ID, 0))
                .isInstanceOf(InvalidOrderDataException.class)
                .hasMessageContaining("holdDurationSec");
    }

    @Test
    @DisplayName("addItem() appends a line whose subtotal is unitPrice x quantity")
    void addItem_succeeds() {
        Order order = aPendingOrder();

        order.addItem(TICKET_TYPE_ID, 2, PRICE);

        assertThat(order.getItems()).hasSize(1);
        OrderItem item = order.getItems().get(0);
        assertThat(item.getOrderId()).as("the line points back at its order").isEqualTo(order.getId());
        assertThat(item.getTicketTypeId()).isEqualTo(TICKET_TYPE_ID);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getUnitPrice()).isEqualTo(PRICE);
        assertThat(item.getSubtotal()).isEqualTo(Money.of(new BigDecimal("300000")));
    }

    @Test
    @DisplayName("addItem() recalculates totalAmount across every line")
    void addItem_recalculatesTotal() {
        Order order = aPendingOrder();

        order.addItem(TICKET_TYPE_ID, 2, PRICE);
        assertThat(order.getTotalAmount()).isEqualTo(Money.of(new BigDecimal("300000")));

        order.addItem(UUID.randomUUID(), 1, Money.of(new BigDecimal("50000")));
        assertThat(order.getTotalAmount()).isEqualTo(Money.of(new BigDecimal("350000")));
    }

    @Test
    @DisplayName("addItem() throws InvalidOrderItemException when quantity is not positive")
    void addItem_rejectsNonPositiveQuantity() {
        Order order = aPendingOrder();

        assertThatThrownBy(() -> order.addItem(TICKET_TYPE_ID, 0, PRICE))
                .isInstanceOf(InvalidOrderItemException.class)
                .hasMessageContaining("quantity");

        assertThat(order.getItems()).as("nothing was appended").isEmpty();
        assertThat(order.getTotalAmount().isZero()).isTrue();
    }

    @Test
    @DisplayName("addItem() throws InvalidOrderStateException once the order has been paid")
    void addItem_throwsAfterPay() {
        Order order = aPendingOrder();
        order.addItem(TICKET_TYPE_ID, 1, PRICE);
        order.pay();

        assertThatThrownBy(() -> order.addItem(TICKET_TYPE_ID, 1, PRICE))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("PAID");

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotalAmount()).as("the charged total is untouched").isEqualTo(PRICE);
    }

    @Test
    @DisplayName("pay() moves PENDING_PAYMENT to PAID and stamps paidAt")
    void pay_succeedsFromPending() {
        Order order = aPendingOrder();

        order.pay();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(order.getPaidAt()).isAfterOrEqualTo(order.getReservedAt());
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getCreatedAt());
    }

    @Test
    @DisplayName("pay() twice throws DuplicatePaymentException, not the generic state error")
    void pay_twiceThrowsDuplicatePayment() {
        Order order = anOrderIn(OrderStatus.PAID);
        Instant firstPaidAt = order.getPaidAt();

        assertThatThrownBy(order::pay)
                .isExactlyInstanceOf(DuplicatePaymentException.class);

        assertThat(order.getPaidAt()).as("the first payment timestamp stands").isEqualTo(firstPaidAt);
    }

    @Test
    @DisplayName("pay() on a CANCELLED order throws InvalidOrderStateException, not DuplicatePayment")
    void pay_onCancelledThrowsStateError() {
        Order order = anOrderIn(OrderStatus.CANCELLED);

        assertThatThrownBy(order::pay)
                .isExactlyInstanceOf(InvalidOrderStateException.class);

        assertThat(order.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("expire() moves PENDING_PAYMENT to EXPIRED")
    void expire_succeedsFromPending() {
        Order order = aPendingOrder();

        order.expire();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("expire() on a PAID order throws — the worker must skip, not rely on a no-op")
    void expire_throwsFromPaid() {
        Order order = anOrderIn(OrderStatus.PAID);

        assertThatThrownBy(order::expire)
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(order.getStatus()).as("still PAID").isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("cancel() moves PENDING_PAYMENT to CANCELLED and throws from EXPIRED")
    void cancel_succeedsFromPendingOnly() {
        Order pending = aPendingOrder();
        pending.cancel();
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertThatThrownBy(anOrderIn(OrderStatus.EXPIRED)::cancel)
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    @DisplayName("fail() moves PENDING_PAYMENT to FAILED and throws from PAID")
    void fail_succeedsFromPendingOnly() {
        Order pending = aPendingOrder();
        pending.fail();
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.FAILED);

        assertThatThrownBy(anOrderIn(OrderStatus.PAID)::fail)
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    @DisplayName("a settled order rejects every further transition")
    void terminalStatuses_rejectEveryMutator() {
        for (OrderStatus terminal : EnumSet.of(OrderStatus.PAID, OrderStatus.EXPIRED,
                OrderStatus.CANCELLED, OrderStatus.FAILED)) {
            Order order = anOrderIn(terminal);

            assertThatThrownBy(order::pay).isInstanceOf(DomainException.class);
            assertThatThrownBy(order::expire).isInstanceOf(InvalidOrderStateException.class);
            assertThatThrownBy(order::cancel).isInstanceOf(InvalidOrderStateException.class);
            assertThatThrownBy(order::fail).isInstanceOf(InvalidOrderStateException.class);
            assertThat(order.getStatus()).as("%s is unchanged", terminal).isEqualTo(terminal);
        }
    }

    @Test
    @DisplayName("every OrderStatus is reachable through a public mutator — no dead status")
    void everyStatus_isReachable() {
        Set<OrderStatus> reached = EnumSet.noneOf(OrderStatus.class);

        for (OrderStatus status : OrderStatus.values()) {
            reached.add(anOrderIn(status).getStatus());
        }

        assertThat(reached).containsExactlyInAnyOrder(OrderStatus.values());
    }

    @Test
    @DisplayName("getItems() returns an unmodifiable view, never the live backing list")
    void getItems_isUnmodifiable() {
        Order order = aPendingOrder();
        order.addItem(TICKET_TYPE_ID, 1, PRICE);

        List<OrderItem> items = order.getItems();

        assertThatThrownBy(() -> items.add(items.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(order.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("reconstitute() restores a paid, multi-line order that create() could not produce")
    void reconstitute_preservesStoredState() {
        UUID id = UUID.randomUUID();
        Instant reservedAt = Instant.parse("2026-05-01T09:00:00Z");
        Instant paidAt = Instant.parse("2026-05-01T09:02:30Z");
        Money total = Money.of(new BigDecimal("300000"));
        OrderItem stored = OrderItem.reconstitute(UUID.randomUUID(), id, TICKET_TYPE_ID, 2, PRICE, total);

        Order order = Order.reconstitute(id, "ORD-0001", USER_ID, EVENT_ID, OrderStatus.PAID, total,
                List.of(stored), reservedAt, reservedAt.plusSeconds(HOLD_SEC), paidAt,
                reservedAt, paidAt);

        assertThat(order.getStatus()).as("create() would force PENDING_PAYMENT").isEqualTo(OrderStatus.PAID);
        assertThat(order.getTotalAmount()).as("create() would force zero").isEqualTo(total);
        assertThat(order.getItems()).as("create() would force an empty order").containsExactly(stored);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
        assertThat(order.getCreatedAt()).as("create() would force now()").isEqualTo(reservedAt);
        assertThatThrownBy(order::expire)
                .as("still terminal — guards apply to a reconstituted order")
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    @DisplayName("reconstitute() copies the item list — the caller cannot mutate the order afterwards")
    void reconstitute_copiesTheItemList() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        List<OrderItem> caller = new ArrayList<>();
        caller.add(OrderItem.reconstitute(UUID.randomUUID(), id, TICKET_TYPE_ID, 1, PRICE, PRICE));

        Order order = Order.reconstitute(id, "ORD-0001", USER_ID, EVENT_ID,
                OrderStatus.PENDING_PAYMENT, PRICE, caller, now, now.plusSeconds(HOLD_SEC), null, now, now);

        caller.clear();

        assertThat(order.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("reconstitute() accepts a null paidAt but rejects a null required column")
    void reconstitute_nullRules() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Order unpaid = Order.reconstitute(id, "ORD-0001", USER_ID, EVENT_ID,
                OrderStatus.PENDING_PAYMENT, Money.zero(), List.of(), now, now.plusSeconds(HOLD_SEC),
                null, now, now);
        assertThat(unpaid.getPaidAt()).as("paidAt is nullable until payment").isNull();

        assertThatThrownBy(() -> Order.reconstitute(id, "ORD-0001", USER_ID, EVENT_ID, null,
                Money.zero(), List.of(), now, now.plusSeconds(HOLD_SEC), null, now, now))
                .isInstanceOf(InvalidOrderDataException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("OrderItem.reconstitute() rejects a null required column")
    void orderItemReconstitute_rejectsNull() {
        assertThatThrownBy(() -> OrderItem.reconstitute(UUID.randomUUID(), UUID.randomUUID(),
                TICKET_TYPE_ID, 1, null, PRICE))
                .isInstanceOf(InvalidOrderItemException.class)
                .hasMessageContaining("unitPrice");
    }

    @Test
    @DisplayName("equality is by id alone")
    void equality_isIdentityBased() {
        Order one = aPendingOrder();
        Order two = aPendingOrder();

        assertThat(one).isEqualTo(one);
        assertThat(one).isNotEqualTo(two);

        Instant now = Instant.now();
        Order sameIdDifferentFields = Order.reconstitute(one.getId(), "ORD-9999", UUID.randomUUID(),
                UUID.randomUUID(), OrderStatus.CANCELLED, Money.zero(), List.of(), now,
                now.plusSeconds(HOLD_SEC), null, now, now);

        assertThat(one).isEqualTo(sameIdDifferentFields);
        assertThat(one).hasSameHashCodeAs(sameIdDifferentFields);
    }
}

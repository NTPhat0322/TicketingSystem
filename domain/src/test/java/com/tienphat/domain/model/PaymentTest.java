package com.tienphat.domain.model;

import com.tienphat.domain.exception.DomainException;
import com.tienphat.domain.exception.DuplicatePaymentException;
import com.tienphat.domain.exception.InvalidPaymentDataException;
import com.tienphat.domain.exception.InvalidPaymentStateException;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final Money AMOUNT = Money.of(new BigDecimal("300000"));
    private static final String TX_REF = "VNPAY-20260601-0001";

    private static Payment aPendingPayment() {
        return Payment.initiate(ORDER_ID, PaymentProvider.VNPAY, AMOUNT, TX_REF);
    }

    private static Payment aPaymentIn(PaymentStatus status) {
        Payment payment = aPendingPayment();
        switch (status) {
            case PENDING -> { }
            case SUCCESS -> payment.markSuccess();
            case FAILED -> payment.markFailed();
        }
        return payment;
    }

    @Test
    @DisplayName("initiate() opens a PENDING attempt that has not collected anything yet")
    void initiate_succeeds() {
        Payment payment = aPendingPayment();

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(payment.getProvider()).isEqualTo(PaymentProvider.VNPAY);
        assertThat(payment.getAmount()).isEqualTo(AMOUNT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getTransactionRef()).isEqualTo(TX_REF);
        assertThat(payment.getPaidAt()).as("no money collected yet").isNull();
        assertThat(payment.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("initiate() rejects a zero amount — a free order must not reach a gateway")
    void initiate_rejectsZeroAmount() {
        assertThatThrownBy(() -> Payment.initiate(ORDER_ID, PaymentProvider.VNPAY, Money.zero(), TX_REF))
                .isInstanceOf(InvalidPaymentDataException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("initiate() rejects a blank transactionRef — it is the gateway's only handle on us")
    void initiate_rejectsBlankTransactionRef() {
        assertThatThrownBy(() -> Payment.initiate(ORDER_ID, PaymentProvider.VNPAY, AMOUNT, "   "))
                .isInstanceOf(InvalidPaymentDataException.class)
                .hasMessageContaining("transactionRef");

        assertThatThrownBy(() -> Payment.initiate(ORDER_ID, PaymentProvider.VNPAY, AMOUNT, null))
                .isInstanceOf(InvalidPaymentDataException.class)
                .hasMessageContaining("transactionRef");
    }

    @Test
    @DisplayName("initiate() rejects a null orderId, provider or amount")
    void initiate_rejectsNullRequiredFields() {
        assertThatThrownBy(() -> Payment.initiate(null, PaymentProvider.VNPAY, AMOUNT, TX_REF))
                .isInstanceOf(InvalidPaymentDataException.class)
                .hasMessageContaining("orderId");

        assertThatThrownBy(() -> Payment.initiate(ORDER_ID, null, AMOUNT, TX_REF))
                .isInstanceOf(InvalidPaymentDataException.class)
                .hasMessageContaining("provider");

        assertThatThrownBy(() -> Payment.initiate(ORDER_ID, PaymentProvider.VNPAY, null, TX_REF))
                .isInstanceOf(InvalidPaymentDataException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("every PaymentProvider can open an attempt — no unusable enum constant")
    void initiate_acceptsEveryProvider() {
        for (PaymentProvider provider : PaymentProvider.values()) {
            Payment payment = Payment.initiate(ORDER_ID, provider, AMOUNT, TX_REF + "-" + provider);

            assertThat(payment.getProvider()).as("provider %s", provider).isEqualTo(provider);
        }
    }

    @Test
    @DisplayName("markSuccess() settles a PENDING attempt and stamps paidAt")
    void markSuccess_succeedsFromPending() {
        Payment payment = aPendingPayment();

        payment.markSuccess();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getPaidAt()).isAfterOrEqualTo(payment.getCreatedAt());
    }

    @Test
    @DisplayName("markSuccess() twice throws DuplicatePaymentException — the webhook replay guard")
    void markSuccess_twiceThrowsDuplicatePayment() {
        Payment payment = aPaymentIn(PaymentStatus.SUCCESS);
        Instant firstPaidAt = payment.getPaidAt();

        assertThatThrownBy(payment::markSuccess)
                .isExactlyInstanceOf(DuplicatePaymentException.class);

        assertThat(payment.getPaidAt()).as("the first settlement timestamp stands").isEqualTo(firstPaidAt);
    }

    @Test
    @DisplayName("the duplicate-payment message carries the gateway reference and the first paidAt")
    void markSuccess_twiceReportsTheFirstSettlement() {
        Payment payment = aPaymentIn(PaymentStatus.SUCCESS);

        assertThatThrownBy(payment::markSuccess)
                .hasMessageContaining(TX_REF)
                .hasMessageContaining(payment.getPaidAt().toString());
    }

    @Test
    @DisplayName("markSuccess() on a FAILED attempt throws InvalidPaymentStateException, not DuplicatePayment")
    void markSuccess_onFailedThrowsStateError() {
        Payment payment = aPaymentIn(PaymentStatus.FAILED);

        assertThatThrownBy(payment::markSuccess)
                .isExactlyInstanceOf(InvalidPaymentStateException.class);

        assertThat(payment.getPaidAt()).as("a rejected attempt is never stamped").isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("markFailed() records a rejected charge from PENDING")
    void markFailed_succeedsFromPending() {
        Payment payment = aPendingPayment();

        payment.markFailed();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("markFailed() on a SUCCESS attempt throws — collected money is undone by a refund, not a flag")
    void markFailed_onSuccessThrows() {
        Payment payment = aPaymentIn(PaymentStatus.SUCCESS);
        Instant paidAt = payment.getPaidAt();

        assertThatThrownBy(payment::markFailed)
                .isInstanceOf(InvalidPaymentStateException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("markFailed() twice throws — no silent no-op")
    void markFailed_twiceThrows() {
        Payment payment = aPaymentIn(PaymentStatus.FAILED);

        assertThatThrownBy(payment::markFailed)
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    @DisplayName("a settled attempt rejects every further mutator")
    void terminalStatuses_rejectEveryMutator() {
        for (PaymentStatus terminal : EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED)) {
            Payment payment = aPaymentIn(terminal);

            assertThatThrownBy(payment::markSuccess).isInstanceOf(DomainException.class);
            assertThatThrownBy(payment::markFailed).isInstanceOf(InvalidPaymentStateException.class);
            assertThat(payment.getStatus()).as("%s is unchanged", terminal).isEqualTo(terminal);
        }
    }

    @Test
    @DisplayName("every PaymentStatus is reachable through a public mutator — no dead status")
    void everyStatus_isReachable() {
        Set<PaymentStatus> reached = EnumSet.noneOf(PaymentStatus.class);

        for (PaymentStatus status : PaymentStatus.values()) {
            reached.add(aPaymentIn(status).getStatus());
        }

        assertThat(reached).containsExactlyInAnyOrder(PaymentStatus.values());
    }

    @Test
    @DisplayName("reconstitute() restores a settled attempt so a replayed webhook is still caught")
    void reconstitute_preservesStoredState() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T09:00:00Z");
        Instant paidAt = Instant.parse("2026-06-01T09:01:12Z");

        Payment payment = Payment.reconstitute(id, ORDER_ID, PaymentProvider.MOMO, AMOUNT,
                PaymentStatus.SUCCESS, TX_REF, paidAt, createdAt);

        assertThat(payment.getStatus()).as("initiate() would force PENDING").isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).as("initiate() would force null").isEqualTo(paidAt);
        assertThat(payment.getCreatedAt()).as("initiate() would force now()").isEqualTo(createdAt);
        assertThatThrownBy(payment::markSuccess)
                .as("a replay after a restart is still a replay")
                .isExactlyInstanceOf(DuplicatePaymentException.class);
    }

    @Test
    @DisplayName("reconstitute() accepts a null paidAt but rejects a null required column")
    void reconstitute_nullRules() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        Payment pending = Payment.reconstitute(id, ORDER_ID, PaymentProvider.STRIPE, AMOUNT,
                PaymentStatus.PENDING, TX_REF, null, createdAt);
        assertThat(pending.getPaidAt()).as("paidAt is nullable until settlement").isNull();

        assertThatThrownBy(() -> Payment.reconstitute(id, ORDER_ID, PaymentProvider.STRIPE, AMOUNT,
                null, TX_REF, null, createdAt))
                .isInstanceOf(InvalidPaymentDataException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("reconstitute() does not re-apply the non-zero rule initiate() enforces")
    void reconstitute_doesNotRevalidateBusinessRules() {
        Payment legacy = Payment.reconstitute(UUID.randomUUID(), ORDER_ID, PaymentProvider.VNPAY,
                Money.zero(), PaymentStatus.SUCCESS, TX_REF, Instant.now(), Instant.now());

        assertThat(legacy.getAmount().isZero())
                .as("a row written under older rules stays readable")
                .isTrue();
    }

    @Test
    @DisplayName("equality is by id alone")
    void equality_isIdentityBased() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Payment one = Payment.reconstitute(id, ORDER_ID, PaymentProvider.VNPAY, AMOUNT,
                PaymentStatus.PENDING, TX_REF, null, now);
        Payment sameIdDifferentFields = Payment.reconstitute(id, UUID.randomUUID(), PaymentProvider.STRIPE,
                Money.of(new BigDecimal("1")), PaymentStatus.FAILED, "OTHER-REF", null, now);

        assertThat(one).isEqualTo(sameIdDifferentFields);
        assertThat(one).hasSameHashCodeAs(sameIdDifferentFields);
        assertThat(one).isNotEqualTo(aPendingPayment());
    }
}

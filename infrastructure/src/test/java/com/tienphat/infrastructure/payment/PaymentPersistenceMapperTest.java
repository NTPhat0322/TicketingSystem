package com.tienphat.infrastructure.payment;

import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.PaymentStatus;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPersistenceMapperTest {

    private final PaymentPersistenceMapper mapper = Mappers.getMapper(PaymentPersistenceMapper.class);

    @Test
    void roundTripsEveryFieldIncludingMoneyAndPaidAt() {
        Payment payment = Payment.initiate(
                UUID.randomUUID(),
                PaymentProvider.VNPAY,
                Money.of(new BigDecimal("999.90")),
                "txn-" + UUID.randomUUID());
        payment.markSuccess();

        Payment roundTripped = mapper.toDomain(mapper.toEntity(payment));

        assertThat(roundTripped.getId()).isEqualTo(payment.getId());
        assertThat(roundTripped.getOrderId()).isEqualTo(payment.getOrderId());
        assertThat(roundTripped.getProvider()).isEqualTo(payment.getProvider());
        assertThat(roundTripped.getAmount()).isEqualTo(payment.getAmount());
        assertThat(roundTripped.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(roundTripped.getTransactionRef()).isEqualTo(payment.getTransactionRef());
        assertThat(roundTripped.getPaidAt()).isEqualTo(payment.getPaidAt());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(payment.getCreatedAt());
    }
}

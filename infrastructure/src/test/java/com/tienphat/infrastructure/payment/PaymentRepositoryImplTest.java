package com.tienphat.infrastructure.payment;

import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.PaymentStatus;
import com.tienphat.domain.vo.Money;
import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class PaymentRepositoryImplTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PaymentRepositoryImpl paymentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void savesAndLoadsPaymentWithEveryField() {
        Payment payment = newPayment(UUID.randomUUID(), "txn-" + UUID.randomUUID());
        payment.markSuccess();

        paymentRepository.save(payment);

        Payment byId = paymentRepository.findById(payment.getId()).orElseThrow();
        Payment byOrder = paymentRepository.findByOrderId(payment.getOrderId()).orElseThrow();
        Payment byTransaction = paymentRepository.findByTransactionRef(payment.getTransactionRef()).orElseThrow();

        assertThat(byId.getId()).isEqualTo(payment.getId());
        assertThat(byId.getOrderId()).isEqualTo(payment.getOrderId());
        assertThat(byId.getProvider()).isEqualTo(payment.getProvider());
        assertThat(byId.getAmount()).isEqualTo(payment.getAmount());
        assertThat(byId.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(byId.getTransactionRef()).isEqualTo(payment.getTransactionRef());
        assertThat(byId.getPaidAt()).isCloseTo(payment.getPaidAt(), within(2, ChronoUnit.MICROS));
        assertThat(byId.getCreatedAt()).isCloseTo(payment.getCreatedAt(), within(2, ChronoUnit.MICROS));
        assertThat(byOrder.getId()).isEqualTo(payment.getId());
        assertThat(byTransaction.getId()).isEqualTo(payment.getId());
    }

    @Test
    void duplicateOrderIdIsRejectedByUniquePaymentConstraint() {
        UUID orderId = UUID.randomUUID();
        paymentRepository.save(newPayment(orderId, "txn-" + UUID.randomUUID()));

        assertThatThrownBy(() -> paymentRepository.save(
                newPayment(orderId, "txn-" + UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateTransactionRefIsRejectedByUniquePaymentConstraint() {
        String transactionRef = "txn-" + UUID.randomUUID();
        paymentRepository.save(newPayment(UUID.randomUUID(), transactionRef));

        assertThatThrownBy(() -> paymentRepository.save(
                newPayment(UUID.randomUUID(), transactionRef)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void callbackLookupCanAcquireAWriteLock() {
        Payment payment = newPayment(UUID.randomUUID(), "txn-" + UUID.randomUUID());
        paymentRepository.save(payment);

        Payment locked = new TransactionTemplate(transactionManager)
                .execute(status -> paymentRepository.findByTransactionRefForUpdate(payment.getTransactionRef()))
                .orElseThrow();

        assertThat(locked)
                .extracting(Payment::getId)
                .isEqualTo(payment.getId());
    }

    private static Payment newPayment(UUID orderId, String transactionRef) {
        return Payment.initiate(
                orderId,
                PaymentProvider.VNPAY,
                Money.of(new BigDecimal("150.00")),
                transactionRef);
    }
}

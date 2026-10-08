package com.tienphat.infrastructure.payment;

import com.tienphat.domain.model.Payment;
import com.tienphat.domain.vo.Money;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;

import java.math.BigDecimal;

/** Maps the pure Payment aggregate to its infrastructure row and back. */
@Mapper(componentModel = "spring")
public interface PaymentPersistenceMapper {

    PaymentJpaEntity toEntity(Payment payment);

    Payment toDomain(PaymentJpaEntity entity);

    default BigDecimal map(Money money) {
        return money == null ? null : money.getAmount();
    }

    default Money map(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }

    @ObjectFactory
    default Payment createDomain(PaymentJpaEntity entity) {
        return Payment.reconstitute(
                entity.getId(),
                entity.getOrderId(),
                entity.getProvider(),
                map(entity.getAmount()),
                entity.getStatus(),
                entity.getTransactionRef(),
                entity.getPaidAt(),
                entity.getCreatedAt());
    }
}

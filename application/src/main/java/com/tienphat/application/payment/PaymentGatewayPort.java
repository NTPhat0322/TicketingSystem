package com.tienphat.application.payment;

import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.vo.Money;

import java.util.Objects;
import java.util.UUID;

/** Provider-neutral boundary used to open a payment attempt. */
public interface PaymentGatewayPort {

    PaymentGatewayInitiation initiate(PaymentGatewayRequest request);

    record PaymentGatewayRequest(UUID orderId, Money amount) {

        public PaymentGatewayRequest {
            Objects.requireNonNull(orderId, "orderId must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
        }
    }

    record PaymentGatewayInitiation(PaymentProvider provider, String transactionRef) {

        public PaymentGatewayInitiation {
            Objects.requireNonNull(provider, "provider must not be null");
            if (transactionRef == null || transactionRef.isBlank()) {
                throw new IllegalArgumentException("transactionRef must not be blank");
            }
        }
    }
}

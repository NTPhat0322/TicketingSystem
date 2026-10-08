package com.tienphat.infrastructure.payment;

import com.tienphat.application.payment.PaymentGatewayPort;
import com.tienphat.domain.model.PaymentProvider;
import org.springframework.stereotype.Component;

/** Deterministic local gateway used until a real provider adapter is introduced. */
@Component
public class LocalPaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public PaymentGatewayInitiation initiate(PaymentGatewayRequest request) {
        return new PaymentGatewayInitiation(
                PaymentProvider.LOCAL,
                "LOCAL-" + request.orderId());
    }
}

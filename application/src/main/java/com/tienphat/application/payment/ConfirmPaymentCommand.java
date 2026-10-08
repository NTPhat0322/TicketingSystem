package com.tienphat.application.payment;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.vo.Money;

public record ConfirmPaymentCommand(
        AuthorizationContext actor,
        String transactionRef,
        PaymentProvider provider,
        Money amount,
        boolean successful) {
}

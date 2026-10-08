package com.tienphat.application.payment;

import com.tienphat.application.auth.AuthorizationContext;

import java.util.UUID;

public record InitiatePaymentCommand(AuthorizationContext actor, UUID orderId) {
}

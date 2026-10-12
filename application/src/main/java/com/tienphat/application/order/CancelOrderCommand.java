package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;

import java.util.UUID;

public record CancelOrderCommand(UUID orderId, AuthorizationContext actor) {
}

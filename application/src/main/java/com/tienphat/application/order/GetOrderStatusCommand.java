package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;

import java.util.UUID;

/** Ownership-aware order polling query. */
public record GetOrderStatusCommand(UUID orderId, AuthorizationContext actor) {
}

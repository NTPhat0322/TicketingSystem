package com.tienphat.application.event;

import com.tienphat.application.auth.AuthorizationContext;

import java.util.UUID;

/** Explicit sale-preparation action; a scheduler is intentionally deferred. */
public record StartSaleCommand(UUID eventId, AuthorizationContext actor) {
}

package com.tienphat.application.order;

import java.util.UUID;

/** Outcome of an idempotent Order-create command. */
public record OrderCreationResult(UUID orderId, boolean created) {
}

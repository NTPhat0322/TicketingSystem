package com.tienphat.application.payment;

import java.util.Locale;
import java.util.UUID;

/** Local/test-safe ticket code strategy; the database unique key remains the final guard. */
public final class UuidTicketCodeGenerator implements TicketCodeGenerator {

    @Override
    public String nextCode(UUID orderId, UUID orderItemId, int sequence) {
        return "TKT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}

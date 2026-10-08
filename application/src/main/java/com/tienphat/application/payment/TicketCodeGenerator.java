package com.tienphat.application.payment;

import java.util.UUID;

/** Generates a customer-facing unique code for one issued ticket. */
public interface TicketCodeGenerator {

    String nextCode(UUID orderId, UUID orderItemId, int sequence);
}

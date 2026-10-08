package com.tienphat.infrastructure.redis;

import java.util.UUID;

/** Stable Redis key names for the reservation boundary. */
public final class RedisReservationKeys {

    public static final String PENDING_INTENTS = "reservation:pending";

    private RedisReservationKeys() {
    }

    public static String stock(UUID ticketTypeId) {
        return "stock:ticket_type:" + requireId(ticketTypeId, "ticketTypeId");
    }

    public static String userLimit(UUID ticketTypeId, UUID userId) {
        return "user:limit:"
                + requireId(ticketTypeId, "ticketTypeId")
                + ":"
                + requireId(userId, "userId");
    }

    public static String hold(UUID orderId) {
        return "hold:order:" + requireId(orderId, "orderId");
    }

    public static String intent(UUID orderId) {
        return "reservation:intent:" + requireId(orderId, "orderId");
    }

    public static String claim(UUID orderId) {
        return "reservation:claim:" + requireId(orderId, "orderId");
    }

    private static String requireId(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value.toString();
    }
}

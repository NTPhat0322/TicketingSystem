package com.tienphat.infrastructure.redis;

/** Hash field names shared by the hold and reservation-intent records. */
public final class RedisReservationFields {

    public static final String ORDER_ID = "orderId";
    public static final String USER_ID = "userId";
    public static final String EVENT_ID = "eventId";
    public static final String TICKET_TYPE_ID = "ticketTypeId";
    public static final String QUANTITY = "quantity";
    public static final String UNIT_PRICE = "unitPrice";
    public static final String MAX_PER_USER = "maxPerUser";
    public static final String HOLD_DURATION_SEC = "holdDurationSec";
    public static final String RESERVED_AT = "reservedAt";
    public static final String EXPIRES_AT = "expiresAt";
    public static final String STATE = "state";
    public static final String HOLD_STATE = "holdState";
    public static final String RETRY_COUNT = "retryCount";
    public static final String NEXT_RETRY_AT = "nextRetryAt";
    public static final String ENQUEUED_AT = "enqueuedAt";

    public static final String HELD = "HELD";
    public static final String RELEASED = "RELEASED";

    private RedisReservationFields() {
    }
}

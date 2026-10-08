package com.tienphat.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the recoverable reservation intent for status polling.
 *
 * <p>The implementation is expected to read the Redis Hash by order id. The application does not
 * depend on Redis key names or serialization details.
 */
public interface ReservationIntentStatusPort {

    Optional<ReservationIntent> findByOrderId(UUID orderId);
}

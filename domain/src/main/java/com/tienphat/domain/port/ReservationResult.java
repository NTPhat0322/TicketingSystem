package com.tienphat.domain.port;

/**
 * Outcome of {@link StockCachePort#tryReserve}.
 *
 * <p>An enum rather than {@code boolean} because the two failures need different messages to the
 * user, and a {@code boolean} would force the adapter to throw to tell them apart.
 */
public enum ReservationResult {

    SUCCESS,
    OUT_OF_STOCK,
    USER_LIMIT_EXCEEDED
}

package com.tienphat.domain.port;

import java.util.UUID;

/**
 * The only way tickets are held. Implemented in {@code infrastructure} by a Redis + Lua adapter,
 * but nothing in this contract names Redis.
 *
 * <p>Every event takes this one path, and Postgres is not touched when a ticket is held --
 * {@code TicketType.soldQuantity} moves only when payment succeeds. The key this port manages is
 * therefore the source of truth for live stock, not a cache of a database value, and must never be
 * rebuilt from Postgres on a miss.
 *
 * <p>Fail-closed: if the backing store is unreachable, implementations must throw rather than
 * fall back to another mechanism. A fallback is a second path that only runs during an outage,
 * which is when it can least afford to be wrong.
 */
public interface StockCachePort {

    /**
     * Atomically checks the user's limit and the remaining stock, then takes both -- or changes
     * nothing. The limit check has to share the atomic step with the stock check; splitting them
     * reintroduces the race.
     *
     * <p>The request records the order identity, ownership, price snapshot, and expiry. The
     * implementation must create the hold and pending intent in the same atomic operation as these
     * counters; callers must not split that sequence into separate method calls.
     *
     * <p>{@link ReservationResult#STOCK_NOT_WARMED} means the required stock key is absent. The
     * adapter must not rebuild inventory from Postgres on a miss.
     */
    ReservationResult tryReserve(ReservationRequest request);

    /**
     * Returns {@code quantity} to the shared stock <em>and</em> to the user's allowance. Both move
     * together, or the user silently loses allowance they are owed. {@code orderId} is the
     * idempotency marker: replaying the same release must not restore stock twice.
     *
     * <p>Callers: hold expiry, user cancellation before payment, and refund/chargeback after
     * payment. Only the refund flow also calls {@code TicketType.releaseSale}; the other two never
     * incremented {@code soldQuantity}, so calling it there would corrupt the counter.
     */
    void release(UUID orderId, UUID ticketTypeId, UUID userId, int quantity);

    /**
     * An un-warmed ticket type reads as zero for this diagnostic read. Reservation itself returns
     * {@link ReservationResult#STOCK_NOT_WARMED} for a missing key, so callers can distinguish it
     * from genuine sold-out inventory.
     */
    int getAvailableStock(UUID ticketTypeId);

    /**
     * Seeds the stock for a ticket type. Mandatory before the event moves to {@code ON_SALE}.
     *
     * <p>Only safe before any hold exists. Calling it once holds are live overwrites stock that
     * Postgres cannot see and hands out tickets that are already taken.
     */
    void warmUp(UUID ticketTypeId, int totalQuantity);
}

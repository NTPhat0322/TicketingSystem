package com.tienphat.domain.port;

import java.util.UUID;

/**
 * The only way tickets are held. Implemented in {@code infrastructure} by a Redis + Lua adapter,
 * but nothing in this contract names Redis.
 *
 * <p>Design doc §2.4: every event takes this one path, and Postgres is not touched when a ticket
 * is held — {@code TicketType.soldQuantity} moves only when payment succeeds. The key this port
 * manages is therefore the source of truth for live stock, not a cache of a database value, and
 * must never be rebuilt from Postgres on a miss.
 *
 * <p>Fail-closed: if the backing store is unreachable, implementations must throw rather than
 * fall back to another mechanism. A fallback is a second path that only runs during an outage,
 * which is when it can least afford to be wrong.
 */
public interface StockCachePort {

    /**
     * Atomically checks the user's limit and the remaining stock, then takes both — or changes
     * nothing. The limit check has to share the atomic step with the stock check; splitting them
     * reintroduces the race.
     *
     * <p>{@code maxPerUser} is passed in rather than looked up, so the adapter stays stateless. The
     * caller loads the {@code TicketType} first; an organizer's mid-sale edit applies only to
     * later calls.
     *
     * @param quantity   must be positive
     * @param maxPerUser must be positive; the cap on tickets this user may hold across the whole
     *                   sale window for this ticket type
     */
    ReservationResult tryReserve(UUID ticketTypeId, UUID userId, int quantity, int maxPerUser);

    /**
     * Returns {@code quantity} to the shared stock <em>and</em> to the user's allowance. Both move
     * together, or the user silently loses allowance they are owed.
     *
     * <p>Callers: hold expiry, user cancellation before payment, and refund/chargeback after
     * payment (a refund restores the allowance — design doc §5). Only the refund flow also calls
     * {@code TicketType.releaseSale}; the other two never incremented {@code soldQuantity}, so
     * calling it there would corrupt the counter.
     */
    void release(UUID ticketTypeId, UUID userId, int quantity);

    /**
     * An un-warmed ticket type reads as zero — indistinguishable from sold out. See
     * {@link #warmUp}.
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

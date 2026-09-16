package com.tienphat.domain.exception;

/**
 * A sale was confirmed for more tickets than the Postgres counter has room for.
 *
 * <p>In a healthy system this never fires: Redis already guaranteed the quantity when the hold was
 * taken. Seeing it means Redis and Postgres have diverged — treat it as a data-integrity signal,
 * not a routine rejection.
 */
public class InsufficientStockException extends DomainException {

    public InsufficientStockException(String message) {
        super(message);
    }
}

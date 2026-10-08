package com.tienphat.domain.model;

/**
 * The gateway a payment was routed through.
 *
 * <p>An enum rather than a free-text column so that adding a provider is a deliberate code change
 * with a compiler-enforced sweep of every {@code switch} over it, not a string appearing in
 * production one day. {@code LOCAL} is the deterministic adapter used by the MVP and tests;
 * the external providers remain deferred to a later phase.
 */
public enum PaymentProvider {

    LOCAL,
    VNPAY,
    MOMO,
    STRIPE
}

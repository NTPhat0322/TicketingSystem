package com.tienphat.domain.repository;

import com.tienphat.domain.model.Payment;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link Payment} persistence. Owned by the domain, implemented in {@code infrastructure}.
 *
 * <p>{@code findByOrderId} returns an {@code Optional}, not a {@code List}: the schema puts a unique
 * constraint on {@code payments.order_id} (design doc §4), so an order has at most one attempt. If a
 * retry flow is ever modeled, that constraint and this signature change together.
 *
 * <p>{@code findByTransactionRef} is what the webhook handler calls first — the gateway knows its
 * own reference, not our ids, and looking up by it is how a replayed callback is recognised before
 * it reaches the domain guard.
 */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByTransactionRef(String transactionRef);
}

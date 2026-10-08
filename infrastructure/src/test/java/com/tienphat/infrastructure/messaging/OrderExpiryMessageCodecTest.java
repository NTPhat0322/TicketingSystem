package com.tienphat.infrastructure.messaging;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderExpiryMessageCodecTest {

    private final OrderExpiryMessageCodec codec = new OrderExpiryMessageCodec();

    @Test
    void roundTripsVersionedExpirySnapshot() {
        OrderExpiryMessage message = new OrderExpiryMessage(
                OrderExpiryMessage.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                new BigDecimal("150000.00"),
                Instant.parse("2026-09-28T08:00:00Z").toString(),
                Instant.parse("2026-09-28T08:05:00Z").toString());

        assertThat(codec.decode(codec.encode(message))).isEqualTo(message);
    }
}

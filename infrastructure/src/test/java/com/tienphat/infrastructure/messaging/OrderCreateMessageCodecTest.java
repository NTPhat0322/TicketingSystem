package com.tienphat.infrastructure.messaging;

import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCreateMessageCodecTest {

    private final OrderCreateMessageCodec codec = new OrderCreateMessageCodec();

    @Test
    void roundTripPreservesVersionedSnapshot() {
        UUID orderId = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
        Instant reservedAt = Instant.parse("2026-09-28T08:00:00.123456Z");
        ReservationIntent intent = new ReservationIntent(
                orderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                Money.of(new BigDecimal("150000")),
                4,
                300,
                reservedAt,
                reservedAt.plusSeconds(300),
                ReservationIntentState.PENDING,
                0,
                reservedAt);

        OrderCreateMessage original = OrderCreateMessage.fromIntent(intent);
        OrderCreateMessage decoded = codec.decode(codec.encode(original));

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.toCommand().unitPrice()).isEqualTo(Money.of(new BigDecimal("150000")));
    }
}

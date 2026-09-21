package com.tienphat.infrastructure.tickettype;

import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTypePersistenceMapperTest {

    private final TicketTypePersistenceMapper mapper = Mappers.getMapper(TicketTypePersistenceMapper.class);

    @Test
    void roundTripsEveryFieldIncludingPriceAndVersion() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        TicketType ticketType = TicketType.reconstitute(
                UUID.randomUUID(), UUID.randomUUID(), "VIP", Money.of(new BigDecimal("199.99")),
                100, 42, 4, 300, 7, TicketTypeStatus.ACTIVE, now, now.plus(1, ChronoUnit.HOURS));

        TicketTypeJpaEntity entity = mapper.toEntity(ticketType);
        TicketType roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(ticketType.getId());
        assertThat(roundTripped.getEventId()).isEqualTo(ticketType.getEventId());
        assertThat(roundTripped.getName()).isEqualTo(ticketType.getName());
        assertThat(roundTripped.getPrice()).isEqualTo(ticketType.getPrice());
        assertThat(roundTripped.getTotalQuantity()).isEqualTo(ticketType.getTotalQuantity());
        assertThat(roundTripped.getSoldQuantity()).isEqualTo(ticketType.getSoldQuantity());
        assertThat(roundTripped.getMaxPerUser()).isEqualTo(ticketType.getMaxPerUser());
        assertThat(roundTripped.getHoldDurationSec()).isEqualTo(ticketType.getHoldDurationSec());
        assertThat(roundTripped.getVersion()).isEqualTo(ticketType.getVersion());
        assertThat(roundTripped.getStatus()).isEqualTo(ticketType.getStatus());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(ticketType.getCreatedAt());
        assertThat(roundTripped.getUpdatedAt()).isEqualTo(ticketType.getUpdatedAt());
    }

    @Test
    void preservesZeroVersionAndZeroPrice() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        TicketType ticketType = TicketType.reconstitute(
                UUID.randomUUID(), UUID.randomUUID(), "Free", Money.zero(),
                50, 0, 1, 60, 0, TicketTypeStatus.ACTIVE, now, now);

        TicketType roundTripped = mapper.toDomain(mapper.toEntity(ticketType));

        assertThat(roundTripped.getVersion()).isZero();
        assertThat(roundTripped.getPrice()).isEqualTo(Money.zero());
    }
}

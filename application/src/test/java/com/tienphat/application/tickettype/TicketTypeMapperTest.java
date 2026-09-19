package com.tienphat.application.tickettype;

import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTypeMapperTest {

    private final TicketTypeMapper ticketTypeMapper = Mappers.getMapper(TicketTypeMapper.class);

    @Test
    @DisplayName("toResult() copies every field, converting Money to BigDecimal, via the generated mapper implementation")
    void toResult_copiesEveryFieldAndConvertsMoney() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        TicketType ticketType = TicketType.create(id, eventId, "VIP", Money.of(new BigDecimal("500000")), 100, 4, 600);

        TicketTypeResult result = ticketTypeMapper.toResult(ticketType);

        assertThat(result.id()).isEqualTo(ticketType.getId());
        assertThat(result.eventId()).isEqualTo(ticketType.getEventId());
        assertThat(result.name()).isEqualTo(ticketType.getName());
        assertThat(result.price()).isEqualByComparingTo(ticketType.getPrice().getAmount());
        assertThat(result.totalQuantity()).isEqualTo(ticketType.getTotalQuantity());
        assertThat(result.soldQuantity()).isEqualTo(ticketType.getSoldQuantity());
        assertThat(result.maxPerUser()).isEqualTo(ticketType.getMaxPerUser());
        assertThat(result.holdDurationSec()).isEqualTo(ticketType.getHoldDurationSec());
        assertThat(result.version()).isEqualTo(ticketType.getVersion());
        assertThat(result.status()).isEqualTo(ticketType.getStatus());
        assertThat(result.createdAt()).isEqualTo(ticketType.getCreatedAt());
        assertThat(result.updatedAt()).isEqualTo(ticketType.getUpdatedAt());
    }
}

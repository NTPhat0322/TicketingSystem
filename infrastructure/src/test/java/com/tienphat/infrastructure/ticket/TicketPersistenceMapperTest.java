package com.tienphat.infrastructure.ticket;

import com.tienphat.domain.model.Ticket;
import com.tienphat.domain.model.TicketStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketPersistenceMapperTest {

    private final TicketPersistenceMapper mapper = Mappers.getMapper(TicketPersistenceMapper.class);

    @Test
    void roundTripsEveryFieldIncludingCheckedInAt() {
        Ticket ticket = Ticket.issueFor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TKT-" + UUID.randomUUID());
        ticket.checkIn();

        Ticket roundTripped = mapper.toDomain(mapper.toEntity(ticket));

        assertThat(roundTripped.getId()).isEqualTo(ticket.getId());
        assertThat(roundTripped.getTicketCode()).isEqualTo(ticket.getTicketCode());
        assertThat(roundTripped.getOrderItemId()).isEqualTo(ticket.getOrderItemId());
        assertThat(roundTripped.getTicketTypeId()).isEqualTo(ticket.getTicketTypeId());
        assertThat(roundTripped.getOwnerUserId()).isEqualTo(ticket.getOwnerUserId());
        assertThat(roundTripped.getStatus()).isEqualTo(TicketStatus.CHECKED_IN);
        assertThat(roundTripped.getIssuedAt()).isEqualTo(ticket.getIssuedAt());
        assertThat(roundTripped.getCheckedInAt()).isEqualTo(ticket.getCheckedInAt());
    }
}

package com.tienphat.infrastructure.ticket;

import com.tienphat.domain.model.Ticket;
import com.tienphat.domain.model.TicketStatus;
import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class TicketRepositoryImplTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TicketRepositoryImpl ticketRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void savesAndLoadsTicketWithEveryField() {
        Ticket ticket = newTicket("TKT-" + UUID.randomUUID());
        ticket.checkIn();

        ticketRepository.save(ticket);
        Ticket found = ticketRepository.findByTicketCode(ticket.getTicketCode()).orElseThrow();

        assertThat(found.getId()).isEqualTo(ticket.getId());
        assertThat(found.getTicketCode()).isEqualTo(ticket.getTicketCode());
        assertThat(found.getOrderItemId()).isEqualTo(ticket.getOrderItemId());
        assertThat(found.getTicketTypeId()).isEqualTo(ticket.getTicketTypeId());
        assertThat(found.getOwnerUserId()).isEqualTo(ticket.getOwnerUserId());
        assertThat(found.getStatus()).isEqualTo(TicketStatus.CHECKED_IN);
        assertThat(found.getIssuedAt()).isCloseTo(ticket.getIssuedAt(), within(2, ChronoUnit.MICROS));
        assertThat(found.getCheckedInAt()).isCloseTo(ticket.getCheckedInAt(), within(2, ChronoUnit.MICROS));
    }

    @Test
    void findsAllTicketsForOneOrderItem() {
        UUID orderItemId = UUID.randomUUID();
        Ticket first = Ticket.issueFor(orderItemId, UUID.randomUUID(), UUID.randomUUID(), "TKT-" + UUID.randomUUID());
        Ticket second = Ticket.issueFor(orderItemId, UUID.randomUUID(), UUID.randomUUID(), "TKT-" + UUID.randomUUID());
        Ticket other = Ticket.issueFor(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TKT-" + UUID.randomUUID());
        ticketRepository.save(first);
        ticketRepository.save(second);
        ticketRepository.save(other);

        List<Ticket> found = ticketRepository.findAllByOrderItemId(orderItemId);

        assertThat(found).extracting(Ticket::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void duplicateTicketCodeIsRejectedByDatabaseConstraint() {
        String ticketCode = "TKT-" + UUID.randomUUID();
        ticketRepository.save(newTicket(ticketCode));

        assertThatThrownBy(() -> ticketRepository.save(newTicket(ticketCode)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ticketCodeLookupCanAcquireAWriteLock() {
        Ticket ticket = newTicket("TKT-" + UUID.randomUUID());
        ticketRepository.save(ticket);

        Ticket locked = new TransactionTemplate(transactionManager)
                .execute(status -> ticketRepository.findByTicketCodeForUpdate(ticket.getTicketCode()))
                .orElseThrow();

        assertThat(locked)
                .extracting(Ticket::getId)
                .isEqualTo(ticket.getId());
    }

    private static Ticket newTicket(String ticketCode) {
        return Ticket.issueFor(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ticketCode);
    }
}

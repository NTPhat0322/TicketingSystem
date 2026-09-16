package com.tienphat.domain.model;

import com.tienphat.domain.exception.DomainException;
import com.tienphat.domain.exception.InvalidTicketDataException;
import com.tienphat.domain.exception.InvalidTicketStateException;
import com.tienphat.domain.exception.TicketAlreadyCheckedInException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTest {

    private static final UUID ORDER_ITEM_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final String CODE = "TCK-0001";

    private static Ticket anIssuedTicket() {
        return Ticket.issueFor(ORDER_ITEM_ID, TICKET_TYPE_ID, OWNER_ID, CODE);
    }

    private static Ticket aTicketIn(TicketStatus status) {
        Ticket ticket = anIssuedTicket();
        switch (status) {
            case ISSUED -> { }
            case CHECKED_IN -> ticket.checkIn();
            case CANCELLED -> ticket.cancel();
        }
        return ticket;
    }

    @Test
    @DisplayName("issueFor() returns an ISSUED ticket that has not been admitted yet")
    void issueFor_succeeds() {
        Ticket ticket = anIssuedTicket();

        assertThat(ticket.getId()).isNotNull();
        assertThat(ticket.getTicketCode()).isEqualTo(CODE);
        assertThat(ticket.getOrderItemId()).isEqualTo(ORDER_ITEM_ID);
        assertThat(ticket.getTicketTypeId()).isEqualTo(TICKET_TYPE_ID);
        assertThat(ticket.getOwnerUserId()).isEqualTo(OWNER_ID);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED);
        assertThat(ticket.getIssuedAt()).isNotNull();
        assertThat(ticket.getCheckedInAt()).as("nobody has walked through the gate").isNull();
    }

    @Test
    @DisplayName("issueFor() mints a distinct id per ticket — one order item yields N independent seats")
    void issueFor_mintsDistinctIds() {
        Ticket one = Ticket.issueFor(ORDER_ITEM_ID, TICKET_TYPE_ID, OWNER_ID, "TCK-0001");
        Ticket two = Ticket.issueFor(ORDER_ITEM_ID, TICKET_TYPE_ID, OWNER_ID, "TCK-0002");

        assertThat(one.getId()).isNotEqualTo(two.getId());
        assertThat(one.getOrderItemId()).as("both belong to the same order line").isEqualTo(two.getOrderItemId());
    }

    @Test
    @DisplayName("issueFor() rejects a blank ticketCode — the QR payload cannot be empty")
    void issueFor_rejectsBlankTicketCode() {
        assertThatThrownBy(() -> Ticket.issueFor(ORDER_ITEM_ID, TICKET_TYPE_ID, OWNER_ID, "   "))
                .isInstanceOf(InvalidTicketDataException.class)
                .hasMessageContaining("ticketCode");

        assertThatThrownBy(() -> Ticket.issueFor(ORDER_ITEM_ID, TICKET_TYPE_ID, OWNER_ID, null))
                .isInstanceOf(InvalidTicketDataException.class)
                .hasMessageContaining("ticketCode");
    }

    @Test
    @DisplayName("issueFor() rejects a null owner or a null link back to the order line")
    void issueFor_rejectsNullReferences() {
        assertThatThrownBy(() -> Ticket.issueFor(null, TICKET_TYPE_ID, OWNER_ID, CODE))
                .isInstanceOf(InvalidTicketDataException.class)
                .hasMessageContaining("orderItemId");

        assertThatThrownBy(() -> Ticket.issueFor(ORDER_ITEM_ID, null, OWNER_ID, CODE))
                .isInstanceOf(InvalidTicketDataException.class)
                .hasMessageContaining("ticketTypeId");

        assertThatThrownBy(() -> Ticket.issueFor(ORDER_ITEM_ID, TICKET_TYPE_ID, null, CODE))
                .isInstanceOf(InvalidTicketDataException.class)
                .hasMessageContaining("ownerUserId");
    }

    @Test
    @DisplayName("checkIn() admits an ISSUED ticket and stamps checkedInAt")
    void checkIn_succeedsFromIssued() {
        Ticket ticket = anIssuedTicket();

        ticket.checkIn();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CHECKED_IN);
        assertThat(ticket.getCheckedInAt()).isNotNull();
        assertThat(ticket.getCheckedInAt()).isAfterOrEqualTo(ticket.getIssuedAt());
    }

    @Test
    @DisplayName("checkIn() twice throws TicketAlreadyCheckedInException, not the generic state error")
    void checkIn_twiceThrowsAlreadyCheckedIn() {
        Ticket ticket = aTicketIn(TicketStatus.CHECKED_IN);
        Instant firstScan = ticket.getCheckedInAt();

        assertThatThrownBy(ticket::checkIn)
                .isExactlyInstanceOf(TicketAlreadyCheckedInException.class);

        assertThat(ticket.getCheckedInAt()).as("the first admission timestamp stands").isEqualTo(firstScan);
    }

    @Test
    @DisplayName("the already-checked-in message carries when the holder entered, for gate staff")
    void checkIn_twiceReportsTheFirstScanTime() {
        Ticket ticket = aTicketIn(TicketStatus.CHECKED_IN);

        assertThatThrownBy(ticket::checkIn)
                .hasMessageContaining(CODE)
                .hasMessageContaining(ticket.getCheckedInAt().toString());
    }

    @Test
    @DisplayName("checkIn() on a CANCELLED ticket throws InvalidTicketStateException")
    void checkIn_onCancelledThrowsStateError() {
        Ticket ticket = aTicketIn(TicketStatus.CANCELLED);

        assertThatThrownBy(ticket::checkIn)
                .isExactlyInstanceOf(InvalidTicketStateException.class);

        assertThat(ticket.getCheckedInAt()).as("a refused ticket is never stamped").isNull();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel() voids an ISSUED ticket")
    void cancel_succeedsFromIssued() {
        Ticket ticket = anIssuedTicket();

        ticket.cancel();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(ticket.getCheckedInAt()).isNull();
    }

    @Test
    @DisplayName("cancel() on a CHECKED_IN ticket throws — the admission was already consumed")
    void cancel_throwsFromCheckedIn() {
        Ticket ticket = aTicketIn(TicketStatus.CHECKED_IN);

        assertThatThrownBy(ticket::cancel)
                .isInstanceOf(InvalidTicketStateException.class);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CHECKED_IN);
    }

    @Test
    @DisplayName("cancel() twice throws — no silent no-op")
    void cancel_twiceThrows() {
        Ticket ticket = aTicketIn(TicketStatus.CANCELLED);

        assertThatThrownBy(ticket::cancel)
                .isInstanceOf(InvalidTicketStateException.class);
    }

    @Test
    @DisplayName("a terminal ticket rejects every further mutator")
    void terminalStatuses_rejectEveryMutator() {
        for (TicketStatus terminal : EnumSet.of(TicketStatus.CHECKED_IN, TicketStatus.CANCELLED)) {
            Ticket ticket = aTicketIn(terminal);

            assertThatThrownBy(ticket::checkIn).isInstanceOf(DomainException.class);
            assertThatThrownBy(ticket::cancel).isInstanceOf(InvalidTicketStateException.class);
            assertThat(ticket.getStatus()).as("%s is unchanged", terminal).isEqualTo(terminal);
        }
    }

    @Test
    @DisplayName("every TicketStatus is reachable through a public mutator — no dead status")
    void everyStatus_isReachable() {
        Set<TicketStatus> reached = EnumSet.noneOf(TicketStatus.class);

        for (TicketStatus status : TicketStatus.values()) {
            reached.add(aTicketIn(status).getStatus());
        }

        assertThat(reached).containsExactlyInAnyOrder(TicketStatus.values());
    }

    @Test
    @DisplayName("reconstitute() restores a ticket already scanned at the gate")
    void reconstitute_preservesStoredState() {
        UUID id = UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-06-01T10:00:00Z");
        Instant checkedInAt = Instant.parse("2026-06-15T18:42:00Z");

        Ticket ticket = Ticket.reconstitute(id, CODE, ORDER_ITEM_ID, TICKET_TYPE_ID, OWNER_ID,
                TicketStatus.CHECKED_IN, issuedAt, checkedInAt);

        assertThat(ticket.getStatus()).as("issueFor() would force ISSUED").isEqualTo(TicketStatus.CHECKED_IN);
        assertThat(ticket.getIssuedAt()).as("issueFor() would force now()").isEqualTo(issuedAt);
        assertThat(ticket.getCheckedInAt()).isEqualTo(checkedInAt);
        assertThatThrownBy(ticket::checkIn)
                .as("a ticket loaded as used cannot pass the gate again")
                .isExactlyInstanceOf(TicketAlreadyCheckedInException.class);
    }

    @Test
    @DisplayName("reconstitute() accepts a null checkedInAt but rejects a null required column")
    void reconstitute_nullRules() {
        UUID id = UUID.randomUUID();
        Instant issuedAt = Instant.now();

        Ticket unused = Ticket.reconstitute(id, CODE, ORDER_ITEM_ID, TICKET_TYPE_ID, OWNER_ID,
                TicketStatus.ISSUED, issuedAt, null);
        assertThat(unused.getCheckedInAt()).as("checkedInAt is nullable until admission").isNull();

        assertThatThrownBy(() -> Ticket.reconstitute(id, CODE, ORDER_ITEM_ID, TICKET_TYPE_ID, OWNER_ID,
                null, issuedAt, null))
                .isInstanceOf(InvalidTicketDataException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("equality is by id alone — two tickets with the same code but different ids differ")
    void equality_isIdentityBased() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Ticket one = Ticket.reconstitute(id, CODE, ORDER_ITEM_ID, TICKET_TYPE_ID, OWNER_ID,
                TicketStatus.ISSUED, now, null);
        Ticket sameIdDifferentFields = Ticket.reconstitute(id, "TCK-9999", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), TicketStatus.CANCELLED, now, null);

        assertThat(one).isEqualTo(sameIdDifferentFields);
        assertThat(one).hasSameHashCodeAs(sameIdDifferentFields);
        assertThat(one).isNotEqualTo(anIssuedTicket());
    }
}

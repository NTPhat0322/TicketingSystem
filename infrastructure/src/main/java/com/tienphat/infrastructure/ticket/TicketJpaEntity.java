package com.tienphat.infrastructure.ticket;

import com.tienphat.domain.model.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Infrastructure-only JPA representation of an issued ticket. */
@Entity
@Table(
        name = "tickets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tickets_ticket_code",
                columnNames = "ticket_code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ticket_code", nullable = false)
    private String ticketCode;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;
}

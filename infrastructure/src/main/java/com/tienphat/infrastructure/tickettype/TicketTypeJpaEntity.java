package com.tienphat.infrastructure.tickettype;

import com.tienphat.domain.model.TicketTypeStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketTypeJpaEntity {

    @Id
    private UUID id;
    private UUID eventId;
    private String name;
    private BigDecimal price;
    private int totalQuantity;
    private int soldQuantity;
    private int maxPerUser;
    private int holdDurationSec;

    @Version
    private int version;

    @Enumerated(EnumType.STRING)
    private TicketTypeStatus status;

    private Instant createdAt;
    private Instant updatedAt;
}

package com.tienphat.infrastructure.event;

import com.tienphat.domain.model.EventStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventJpaEntity {

    @Id
    private UUID id;
    private UUID organizerId;
    private String name;
    private String description;
    private String venueName;
    private Instant startTime;
    private Instant endTime;
    private Instant saleStartTime;
    private Instant saleEndTime;

    @Enumerated(EnumType.STRING)
    private EventStatus status;

    private Instant createdAt;
    private Instant updatedAt;
}

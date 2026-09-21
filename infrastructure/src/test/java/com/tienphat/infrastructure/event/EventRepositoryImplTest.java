package com.tienphat.infrastructure.event;

import com.tienphat.domain.model.Event;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class EventRepositoryImplTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private EventRepositoryImpl eventRepository;

    private static Event newEvent(Instant createdAt) {
        UUID id = UUID.randomUUID();
        Instant now = createdAt.truncatedTo(ChronoUnit.MICROS);
        return Event.reconstitute(
                id, UUID.randomUUID(), "Concert " + id, "desc", "Venue",
                now.plus(3, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS),
                com.tienphat.domain.model.EventStatus.DRAFT, now, now);
    }

    @Test
    void savesThenFindsByIdWithEveryFieldIntact() {
        Event event = newEvent(Instant.now());

        eventRepository.save(event);
        Optional<Event> found = eventRepository.findById(event.getId());

        assertThat(found).isPresent();
        assertThat(found.get()).usingRecursiveComparison().isEqualTo(event);
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Optional<Event> found = eventRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void findAllReturnsPagesInCreatedAtOrderWithAccurateTotalElements() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<Event> seeded = List.of(
                newEvent(base),
                newEvent(base.plus(1, ChronoUnit.MINUTES)),
                newEvent(base.plus(2, ChronoUnit.MINUTES)),
                newEvent(base.plus(3, ChronoUnit.MINUTES)),
                newEvent(base.plus(4, ChronoUnit.MINUTES))
        );
        seeded.forEach(eventRepository::save);

        PageResult<Event> page0 = eventRepository.findAll(new PageRequest(0, 2));
        PageResult<Event> page1 = eventRepository.findAll(new PageRequest(1, 2));

        assertThat(page0.totalElements()).isGreaterThanOrEqualTo(5);
        assertThat(page0.content()).hasSize(2);
        assertThat(page1.content()).hasSize(2);
        assertThat(page0.content().get(0).getCreatedAt())
                .isBeforeOrEqualTo(page0.content().get(1).getCreatedAt());
        assertThat(page0.content().get(1).getCreatedAt())
                .isBeforeOrEqualTo(page1.content().get(0).getCreatedAt());
        List<UUID> page0Ids = page0.content().stream().map(Event::getId).toList();
        List<UUID> page1Ids = page1.content().stream().map(Event::getId).toList();
        assertThat(page0Ids).doesNotContainAnyElementsOf(page1Ids);
    }
}

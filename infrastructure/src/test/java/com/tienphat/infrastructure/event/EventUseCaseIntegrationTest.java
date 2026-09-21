package com.tienphat.infrastructure.event;

import com.tienphat.application.event.CreateEventCommand;
import com.tienphat.application.event.CreateEventUseCase;
import com.tienphat.application.event.DeactivateEventCommand;
import com.tienphat.application.event.DeactivateEventUseCase;
import com.tienphat.application.event.EventMapper;
import com.tienphat.application.event.EventResult;
import com.tienphat.application.event.GetEventUseCase;
import com.tienphat.application.event.ListEventsUseCase;
import com.tienphat.application.event.UpdateEventCommand;
import com.tienphat.application.event.UpdateEventUseCase;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class EventUseCaseIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private EventRepositoryImpl eventRepository;

    private final EventMapper eventMapper = Mappers.getMapper(EventMapper.class);

    private CreateEventUseCase createEventUseCase;
    private UpdateEventUseCase updateEventUseCase;
    private GetEventUseCase getEventUseCase;
    private ListEventsUseCase listEventsUseCase;
    private DeactivateEventUseCase deactivateEventUseCase;

    @BeforeEach
    void setUp() {
        createEventUseCase = new CreateEventUseCase(eventRepository, eventMapper);
        updateEventUseCase = new UpdateEventUseCase(eventRepository, eventMapper);
        getEventUseCase = new GetEventUseCase(eventRepository, eventMapper);
        listEventsUseCase = new ListEventsUseCase(eventRepository, eventMapper);
        deactivateEventUseCase = new DeactivateEventUseCase(eventRepository, eventMapper);
    }

    @Test
    void runsAllFiveEventUseCasesEndToEndAgainstRealPostgres() {
        Instant saleStart = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        Instant saleEnd = saleStart.plus(5, ChronoUnit.DAYS);
        Instant start = saleEnd.plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);

        EventResult created = createEventUseCase.execute(new CreateEventCommand(
                UUID.randomUUID(), "Concert", "desc", "Venue", start, end, saleStart, saleEnd));
        assertThat(created.status()).isEqualTo(EventStatus.DRAFT);

        EventResult fetched = getEventUseCase.execute(created.id());
        assertThat(fetched.id()).isEqualTo(created.id());

        Instant newStart = start.plus(1, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(3, ChronoUnit.HOURS);
        EventResult updated = updateEventUseCase.execute(new UpdateEventCommand(
                created.id(), "Concert Updated", "desc2", "Venue2", newStart, newEnd, saleStart, saleEnd));
        assertThat(updated.name()).isEqualTo("Concert Updated");

        PageResult<EventResult> page = listEventsUseCase.execute(new PageRequest(0, 10));
        assertThat(page.content()).extracting(EventResult::id).contains(created.id());

        EventResult deactivated = deactivateEventUseCase.execute(new DeactivateEventCommand(created.id()));
        assertThat(deactivated.status()).isEqualTo(EventStatus.CANCELLED);
    }
}

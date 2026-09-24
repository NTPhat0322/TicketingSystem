package com.tienphat.infrastructure.tickettype;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.event.CreateEventCommand;
import com.tienphat.application.event.CreateEventUseCase;
import com.tienphat.application.event.EventMapper;
import com.tienphat.application.event.EventResult;
import com.tienphat.application.tickettype.CreateTicketTypeCommand;
import com.tienphat.application.tickettype.CreateTicketTypeUseCase;
import com.tienphat.application.tickettype.DeactivateTicketTypeCommand;
import com.tienphat.application.tickettype.DeactivateTicketTypeUseCase;
import com.tienphat.application.tickettype.GetTicketTypeUseCase;
import com.tienphat.application.tickettype.ListTicketTypesByEventUseCase;
import com.tienphat.application.tickettype.TicketTypeMapper;
import com.tienphat.application.tickettype.TicketTypeResult;
import com.tienphat.application.tickettype.UpdateTicketTypeCommand;
import com.tienphat.application.tickettype.UpdateTicketTypeUseCase;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import com.tienphat.infrastructure.event.EventRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class TicketTypeUseCaseIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final AuthorizationContext ORGANIZER =
            new AuthorizationContext(ORGANIZER_ID, UserRole.ORGANIZER);

    @Autowired
    private EventRepositoryImpl eventRepository;

    @Autowired
    private TicketTypeRepositoryImpl ticketTypeRepository;

    private final EventMapper eventMapper = Mappers.getMapper(EventMapper.class);
    private final TicketTypeMapper ticketTypeMapper = Mappers.getMapper(TicketTypeMapper.class);

    private CreateEventUseCase createEventUseCase;
    private CreateTicketTypeUseCase createTicketTypeUseCase;
    private UpdateTicketTypeUseCase updateTicketTypeUseCase;
    private GetTicketTypeUseCase getTicketTypeUseCase;
    private ListTicketTypesByEventUseCase listTicketTypesByEventUseCase;
    private DeactivateTicketTypeUseCase deactivateTicketTypeUseCase;

    @BeforeEach
    void setUp() {
        createEventUseCase = new CreateEventUseCase(eventRepository, eventMapper);
        createTicketTypeUseCase = new CreateTicketTypeUseCase(ticketTypeRepository, eventRepository, ticketTypeMapper);
        updateTicketTypeUseCase = new UpdateTicketTypeUseCase(ticketTypeRepository, eventRepository, ticketTypeMapper);
        getTicketTypeUseCase = new GetTicketTypeUseCase(ticketTypeRepository, ticketTypeMapper);
        listTicketTypesByEventUseCase = new ListTicketTypesByEventUseCase(ticketTypeRepository, ticketTypeMapper);
        deactivateTicketTypeUseCase = new DeactivateTicketTypeUseCase(
                ticketTypeRepository, eventRepository, ticketTypeMapper);
    }

    @Test
    void runsAllFiveTicketTypeUseCasesEndToEndAgainstRealPostgres() {
        Instant saleStart = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        Instant saleEnd = saleStart.plus(5, ChronoUnit.DAYS);
        Instant start = saleEnd.plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);
        EventResult event = createEventUseCase.execute(new CreateEventCommand(
                ORGANIZER, "Concert", "desc", "Venue", start, end, saleStart, saleEnd));

        TicketTypeResult created = createTicketTypeUseCase.execute(new CreateTicketTypeCommand(
                event.id(), ORGANIZER, "VIP", new BigDecimal("199.99"), 100, 4, 300));
        assertThat(created.status()).isEqualTo(TicketTypeStatus.ACTIVE);
        assertThat(created.version()).isZero();

        TicketTypeResult fetched = getTicketTypeUseCase.execute(created.id());
        assertThat(fetched.id()).isEqualTo(created.id());

        TicketTypeResult updated = updateTicketTypeUseCase.execute(new UpdateTicketTypeCommand(
                created.id(), ORGANIZER, "VIP Updated", new BigDecimal("249.99"), 120, 6, 600));
        assertThat(updated.name()).isEqualTo("VIP Updated");
        assertThat(updated.price()).isEqualByComparingTo("249.99");

        var listed = listTicketTypesByEventUseCase.execute(event.id());
        assertThat(listed).extracting(TicketTypeResult::id).contains(created.id());

        TicketTypeResult deactivated = deactivateTicketTypeUseCase.execute(
                new DeactivateTicketTypeCommand(created.id(), ORGANIZER));
        assertThat(deactivated.status()).isEqualTo(TicketTypeStatus.CLOSED);
    }
}

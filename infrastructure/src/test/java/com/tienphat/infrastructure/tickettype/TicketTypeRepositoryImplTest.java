package com.tienphat.infrastructure.tickettype;

import com.tienphat.domain.exception.TicketTypeConcurrentUpdateException;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.vo.Money;
import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class TicketTypeRepositoryImplTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TicketTypeRepositoryImpl ticketTypeRepository;

    private static TicketType newTicketType(UUID eventId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return TicketType.reconstitute(
                UUID.randomUUID(), eventId, "VIP", Money.of(new BigDecimal("199.99")),
                100, 42, 4, 300, 0, TicketTypeStatus.ACTIVE, now, now);
    }

    @Test
    void savesThenFindsByIdWithEveryFieldIntact() {
        TicketType ticketType = newTicketType(UUID.randomUUID());

        TicketType saved = ticketTypeRepository.save(ticketType);
        Optional<TicketType> found = ticketTypeRepository.findById(ticketType.getId());

        assertThat(found).isPresent();
        assertThat(found.get()).usingRecursiveComparison().isEqualTo(saved);
        assertThat(found.get().getPrice()).isEqualTo(ticketType.getPrice());
        assertThat(found.get().getVersion()).isEqualTo(saved.getVersion());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Optional<TicketType> found = ticketTypeRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void findAllByEventIdReturnsOnlyThatEventsTicketTypes() {
        UUID eventId = UUID.randomUUID();
        TicketType t1 = newTicketType(eventId);
        TicketType t2 = newTicketType(eventId);
        TicketType other = newTicketType(UUID.randomUUID());
        ticketTypeRepository.save(t1);
        ticketTypeRepository.save(t2);
        ticketTypeRepository.save(other);

        List<TicketType> found = ticketTypeRepository.findAllByEventId(eventId);

        assertThat(found).extracting(TicketType::getId)
                .containsExactlyInAnyOrder(t1.getId(), t2.getId());
    }

    @Test
    void findAllByEventIdReturnsEmptyListForEventWithNone() {
        List<TicketType> found = ticketTypeRepository.findAllByEventId(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void save_staleVersion_throwsConcurrentUpdateException() {
        TicketType original = newTicketType(UUID.randomUUID());
        ticketTypeRepository.save(original);

        TicketType firstEditorCopy = ticketTypeRepository.findById(original.getId()).orElseThrow();
        TicketType secondEditorCopy = ticketTypeRepository.findById(original.getId()).orElseThrow();

        firstEditorCopy.updateDetails("VIP Updated", firstEditorCopy.getPrice(),
                firstEditorCopy.getTotalQuantity(), firstEditorCopy.getMaxPerUser(), firstEditorCopy.getHoldDurationSec());
        ticketTypeRepository.save(firstEditorCopy);

        secondEditorCopy.updateDetails("VIP Stale", secondEditorCopy.getPrice(),
                secondEditorCopy.getTotalQuantity(), secondEditorCopy.getMaxPerUser(), secondEditorCopy.getHoldDurationSec());

        assertThatThrownBy(() -> ticketTypeRepository.save(secondEditorCopy))
                .isInstanceOf(TicketTypeConcurrentUpdateException.class)
                .isNotInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}

package com.tienphat.infrastructure.outbox;

import com.tienphat.domain.model.AggregateType;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.model.OutboxEventStatus;
import com.tienphat.domain.model.OutboxEventType;
import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class OutboxEventRepositoryImplTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OutboxEventRepositoryImpl outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void pendingQueryReturnsOnlyPendingEvents() {
        OutboxEvent pending = newEvent(OutboxEventType.ORDER_CREATED);
        OutboxEvent published = newEvent(OutboxEventType.ORDER_EXPIRED);
        published.markPublished();
        outboxRepository.save(pending);
        outboxRepository.save(published);

        List<OutboxEvent> found = new TransactionTemplate(transactionManager)
                .execute(status -> outboxRepository.findAllPending());

        assertThat(found).extracting(OutboxEvent::getId).contains(pending.getId());
        assertThat(found).extracting(OutboxEvent::getId).doesNotContain(published.getId());
        assertThat(found).allMatch(event -> event.getStatus() == OutboxEventStatus.PENDING);
    }

    @Test
    void skipLockedPendingQueryDoesNotReturnTheSameRowToAnotherPublisher() throws Exception {
        OutboxEvent pending = newEvent(OutboxEventType.ORDER_CREATED);
        outboxRepository.save(pending);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<List<OutboxEvent>> secondResult = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        Thread first = new Thread(() -> transactionTemplate.executeWithoutResult(status -> {
            try {
                List<OutboxEvent> firstResult = outboxRepository.findAllPending();
                assertThat(firstResult).extracting(OutboxEvent::getId).contains(pending.getId());
                firstLocked.countDown();
                assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (Throwable failure) {
                firstFailure.set(failure);
                status.setRollbackOnly();
            }
        }));

        Thread second = new Thread(() -> transactionTemplate.executeWithoutResult(status -> {
            try {
                assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
                secondResult.set(outboxRepository.findAllPending());
            } catch (Throwable failure) {
                secondFailure.set(failure);
                status.setRollbackOnly();
            }
        }));

        first.start();
        assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
        second.start();
        second.join(TimeUnit.SECONDS.toMillis(5));
        releaseFirst.countDown();
        first.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(firstFailure).hasValue(null);
        assertThat(secondFailure).hasValue(null);
        assertThat(secondResult).hasValue(List.of());
    }

    private static OutboxEvent newEvent(OutboxEventType type) {
        return OutboxEvent.record(
                type.ownerType(),
                UUID.randomUUID(),
                type,
                "{\"event\":\"" + type + "\"}");
    }
}

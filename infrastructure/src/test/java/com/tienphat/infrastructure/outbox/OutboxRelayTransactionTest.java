package com.tienphat.infrastructure.outbox;

import com.tienphat.domain.model.AggregateType;
import com.tienphat.domain.model.OutboxEvent;
import com.tienphat.domain.model.OutboxEventType;
import com.tienphat.domain.repository.OutboxEventRepository;
import com.tienphat.infrastructure.config.MessagingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTransactionTest {

    @Test
    void brokerFailureReturnsEventToRetryablePendingState() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventMessagePublisher publisher = mock(OutboxEventMessagePublisher.class);
        MessagingProperties properties = new MessagingProperties();
        OutboxEvent event = OutboxEvent.record(
                AggregateType.PAYMENT,
                UUID.randomUUID(),
                OutboxEventType.PAYMENT_SUCCESS,
                "{\"schemaVersion\":1}");
        when(repository.findAllPending()).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down"))
                .when(publisher).publish(event);

        OutboxRelayTransaction relay = new OutboxRelayTransaction(repository, publisher, properties);

        OutboxRelayTransaction.OutboxPublishResult result = relay.publishPending();

        assertThat(result.published()).isZero();
        assertThat(result.retried()).isEqualTo(1);
        assertThat(event.getStatus().name()).isEqualTo("PENDING");
        verify(repository).save(event);
    }

    @Test
    void successfulPublishMarksEventPublished() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventMessagePublisher publisher = mock(OutboxEventMessagePublisher.class);
        MessagingProperties properties = new MessagingProperties();
        OutboxEvent event = OutboxEvent.record(
                AggregateType.PAYMENT,
                UUID.randomUUID(),
                OutboxEventType.PAYMENT_SUCCESS,
                "{\"schemaVersion\":1}");
        when(repository.findAllPending()).thenReturn(List.of(event));

        OutboxRelayTransaction relay = new OutboxRelayTransaction(repository, publisher, properties);

        OutboxRelayTransaction.OutboxPublishResult result = relay.publishPending();

        assertThat(result.published()).isEqualTo(1);
        assertThat(result.retried()).isZero();
        assertThat(event.getStatus().name()).isEqualTo("PUBLISHED");
        verify(repository).save(event);
        verify(publisher).publish(event);
    }
}

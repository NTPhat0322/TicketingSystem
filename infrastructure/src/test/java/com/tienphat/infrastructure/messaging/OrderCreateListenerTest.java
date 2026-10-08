package com.tienphat.infrastructure.messaging;

import com.tienphat.application.order.CreateOrderFromReservationUseCase;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.repository.OrderRepository;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderCreateListenerTest {

    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");

    private final OrderCreateMessageCodec codec = new OrderCreateMessageCodec();
    private final CreateOrderFromReservationUseCase worker = mock(CreateOrderFromReservationUseCase.class);
    private final ReservationIntentStore intentStore = mock(ReservationIntentStore.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderCreateListener listener =
            new OrderCreateListener(codec, worker, intentStore, orderRepository);

    @Test
    void validMessageMarksIntentAfterWorkerReturnsAndAcknowledges() throws Exception {
        Message message = message(codec.encode(validMessage()), 42L);
        Channel channel = mock(Channel.class);

        listener.onMessage(message, channel);

        verify(worker).execute(any());
        verify(intentStore).markOrderCreated(ORDER_ID);
        verify(channel).basicAck(42L, false);
        verify(channel, never()).basicReject(any(Long.class), any(Boolean.class));
    }

    @Test
    void malformedMessageIsRejectedWithoutTouchingWorker() throws Exception {
        Message message = message("not-json".getBytes(), 43L);
        Channel channel = mock(Channel.class);

        listener.onMessage(message, channel);

        verify(channel).basicReject(43L, false);
        verify(worker, never()).execute(any());
        verify(intentStore, never()).markOrderCreated(any());
    }

    private static OrderCreateMessage validMessage() {
        Instant reservedAt = Instant.parse("2026-09-28T08:00:00Z");
        return new OrderCreateMessage(
                OrderCreateMessage.CURRENT_SCHEMA_VERSION,
                MESSAGE_ID,
                ORDER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                new BigDecimal("150000.00"),
                reservedAt.toString(),
                reservedAt.plusSeconds(300).toString());
    }

    private static Message message(byte[] body, long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(body, properties);
    }
}

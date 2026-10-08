package com.tienphat.infrastructure.messaging;

import com.tienphat.application.order.ExpireOrderCommand;
import com.tienphat.application.order.ExpireOrderUseCase;
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
import static org.mockito.Mockito.when;

class OrderExpiryListenerTest {

    private final OrderExpiryMessageCodec codec = new OrderExpiryMessageCodec();
    private final ExpireOrderUseCase useCase = mock(ExpireOrderUseCase.class);
    private final OrderExpiryListener listener = new OrderExpiryListener(codec, useCase);

    @Test
    void validMessageRunsExpiryAndAcknowledges() throws Exception {
        Message message = message(codec.encode(validMessage()), 10L);
        Channel channel = mock(Channel.class);

        listener.onMessage(message, channel);

        verify(useCase).execute(any(ExpireOrderCommand.class));
        verify(channel).basicAck(10L, false);
    }

    @Test
    void malformedMessageIsRejectedWithoutRunningUseCase() throws Exception {
        Message message = message("invalid".getBytes(), 11L);
        Channel channel = mock(Channel.class);

        listener.onMessage(message, channel);

        verify(channel).basicReject(11L, false);
        verify(useCase, never()).execute(any());
    }

    private static OrderExpiryMessage validMessage() {
        Instant reservedAt = Instant.parse("2026-09-28T08:00:00Z");
        return new OrderExpiryMessage(
                OrderExpiryMessage.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21"),
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

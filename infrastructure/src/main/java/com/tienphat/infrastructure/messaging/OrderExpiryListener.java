package com.tienphat.infrastructure.messaging;

import com.tienphat.application.order.ExpireOrderCommand;
import com.tienphat.application.order.ExpireOrderUseCase;
import com.tienphat.domain.exception.InvalidOrderDataException;
import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Consumes DLX expiry signals and acknowledges only after DB transition plus Redis release succeed. */
@Component
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = {"enabled", "consumers-enabled"},
        havingValue = "true")
public class OrderExpiryListener {

    private static final Logger LOG = LoggerFactory.getLogger(OrderExpiryListener.class);

    private final OrderExpiryMessageCodec codec;
    private final ExpireOrderUseCase expireOrderUseCase;

    public OrderExpiryListener(
            OrderExpiryMessageCodec codec,
            ExpireOrderUseCase expireOrderUseCase) {
        this.codec = codec;
        this.expireOrderUseCase = expireOrderUseCase;
    }

    @RabbitListener(
            queues = "${ticketing.messaging.order-expire-queue}",
            containerFactory = "orderCreateListenerContainerFactory")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        OrderExpiryMessage wireMessage;
        ExpireOrderCommand command;
        try {
            wireMessage = codec.decode(message.getBody());
            command = wireMessage.toCommand();
        } catch (RuntimeException exception) {
            LOG.error("Rejecting malformed order-expiry message", exception);
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            expireOrderUseCase.execute(command);
            LOG.info("Processed order expiry orderId={} ticketTypeId={}",
                    command.orderId(), command.ticketTypeId());
            channel.basicAck(deliveryTag, false);
        } catch (InvalidOrderDataException | InvalidReservationRequestException exception) {
            LOG.error("Rejecting invalid order-expiry message orderId={} ticketTypeId={}",
                    command.orderId(), command.ticketTypeId(), exception);
            channel.basicReject(deliveryTag, false);
        }
    }
}

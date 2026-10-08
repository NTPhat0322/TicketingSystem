package com.tienphat.infrastructure.messaging;

import com.tienphat.application.order.CreateOrderFromReservationCommand;
import com.tienphat.application.order.CreateOrderFromReservationUseCase;
import com.tienphat.domain.exception.InvalidOrderDataException;
import com.tienphat.domain.exception.InvalidReservationRequestException;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.util.Optional;

/** Consumes order-create messages and acknowledges only after the DB transaction has committed. */
@Component
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = {"enabled", "consumers-enabled"},
        havingValue = "true")
public class OrderCreateListener {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCreateListener.class);

    private final OrderCreateMessageCodec codec;
    private final CreateOrderFromReservationUseCase worker;
    private final ReservationIntentStore intentStore;
    private final OrderRepository orderRepository;

    public OrderCreateListener(
            OrderCreateMessageCodec codec,
            CreateOrderFromReservationUseCase worker,
            ReservationIntentStore intentStore,
            OrderRepository orderRepository) {
        this.codec = codec;
        this.worker = worker;
        this.intentStore = intentStore;
        this.orderRepository = orderRepository;
    }

    @RabbitListener(
            queues = "${ticketing.messaging.order-create-queue}",
            containerFactory = "orderCreateListenerContainerFactory")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        OrderCreateMessage wireMessage;
        CreateOrderFromReservationCommand command;
        try {
            wireMessage = codec.decode(message.getBody());
            command = wireMessage.toCommand();
        } catch (RuntimeException exception) {
            LOG.error("Rejecting malformed order-create message", exception);
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            Optional<ReservationIntent> intent = intentStore.findByOrderId(command.orderId());
            if (intent.isPresent() && intent.get().state().isTerminal()) {
                LOG.info("Acknowledging order-create for terminal reservation orderId={} ticketTypeId={}",
                        command.orderId(), command.ticketTypeId());
                channel.basicAck(deliveryTag, false);
                return;
            }
            worker.execute(command);
        } catch (DataIntegrityViolationException exception) {
            if (orderRepository.findById(command.orderId()).isEmpty()) {
                throw exception;
            }
            LOG.info("Acknowledging concurrent duplicate order-create delivery for order {}",
                    command.orderId());
        } catch (InvalidOrderDataException | InvalidReservationRequestException exception) {
            LOG.error("Rejecting invalid order-create message for order {}",
                    command.orderId(), exception);
            channel.basicReject(deliveryTag, false);
            return;
        }

        // The worker's @Transactional proxy has returned only after commit.
        intentStore.markOrderCreated(command.orderId());
        channel.basicAck(deliveryTag, false);
    }
}

package com.tienphat.infrastructure.messaging;

import com.tienphat.infrastructure.InfrastructureTestApplication;
import com.tienphat.infrastructure.config.RabbitTopologyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class RedisRabbitTopologySmokeTest extends AbstractRedisRabbitIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void talksToRealRedisContainer() {
        redisTemplate.opsForValue().set("phase1:smoke", "ok");

        assertThat(redisTemplate.opsForValue().get("phase1:smoke")).isEqualTo("ok");
    }

    @Test
    void declaresAndUsesDurableOrderTopologyOnRealRabbitMq() {
        assertThat(amqpAdmin.getQueueProperties(RabbitTopologyConfig.ORDER_CREATE_QUEUE)).isNotNull();

        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.ORDER_EXCHANGE,
                RabbitTopologyConfig.ORDER_CREATE_ROUTING_KEY,
                "phase1-smoke",
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                });

        Message message = rabbitTemplate.receive(RabbitTopologyConfig.ORDER_CREATE_QUEUE, 5_000);

        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(new String(message.getBody())).isEqualTo("phase1-smoke");
    }
}

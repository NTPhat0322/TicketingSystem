package com.tienphat.infrastructure.messaging;

import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public abstract class AbstractRedisRabbitIntegrationTest extends AbstractPostgresIntegrationTest {

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    static final GenericContainer<?> RABBITMQ = new GenericContainer<>("rabbitmq:4-management")
            .withEnv("RABBITMQ_DEFAULT_USER", "test")
            .withEnv("RABBITMQ_DEFAULT_PASS", "test")
            .withExposedPorts(5672)
            .waitingFor(Wait.forListeningPort());

    static {
        REDIS.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void registerMessagingProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "test");
        registry.add("spring.rabbitmq.password", () -> "test");
        registry.add("ticketing.messaging.enabled", () -> "true");
    }
}

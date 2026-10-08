package com.tienphat.ticketingsystem.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienphat.domain.model.Event;
import com.tienphat.domain.model.EventStatus;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.model.User;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.StockCachePort;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.PaymentRepository;
import com.tienphat.domain.repository.TicketRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.repository.UserRepository;
import com.tienphat.domain.vo.Money;
import com.tienphat.infrastructure.config.MessagingProperties;
import com.tienphat.infrastructure.config.RabbitTopologyConfig;
import com.tienphat.infrastructure.messaging.OrderCreateMessage;
import com.tienphat.infrastructure.messaging.OrderCreateMessageCodec;
import com.tienphat.infrastructure.messaging.OrderCreatePublisher;
import com.tienphat.infrastructure.messaging.OrderCreateListener;
import com.tienphat.infrastructure.messaging.OrderExpiryListener;
import com.tienphat.infrastructure.reconciliation.ReservationReconciliationJob;
import com.tienphat.infrastructure.messaging.ReservationIntentRelay;
import com.tienphat.infrastructure.redis.RedisReservationAdapter;
import com.tienphat.infrastructure.redis.RedisReservationKeys;
import com.tienphat.ticketingsystem.AbstractPostgresIntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cross-system API tests: PostgreSQL, Redis Lua, RabbitMQ, workers, polling, and payment callback. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TicketPurchaseLifecycleApiE2ETest.FailureInjectionConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
        "ticketing.messaging.enabled=true",
        "ticketing.messaging.consumers-enabled=true",
        "ticketing.messaging.relay-enabled=true",
        "ticketing.messaging.relay-interval=1h",
        "ticketing.messaging.reconciliation-interval=1h",
        "ticketing.messaging.outbox-publisher-enabled=false",
        "ticketing.messaging.outbox-publisher-interval=1h",
        "ticketing.messaging.reservation-intent-retry-delay=50ms",
        "ticketing.messaging.reservation-intent-max-retry-delay=200ms",
        "ticketing.messaging.publisher-confirm-timeout=3s",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.jpa.show-sql=false"
})
class TicketPurchaseLifecycleApiE2ETest extends AbstractPostgresIntegrationTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    private static final GenericContainer<?> RABBITMQ = new GenericContainer<>(
            DockerImageName.parse("rabbitmq:4-management"))
            .withEnv("RABBITMQ_DEFAULT_USER", "test")
            .withEnv("RABBITMQ_DEFAULT_PASS", "test")
            .withExposedPorts(5672);

    static {
        REDIS.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void registerInfrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "test");
        registry.add("spring.rabbitmq.password", () -> "test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ReservationIntentRelay reservationIntentRelay;

    @Autowired
    private com.tienphat.domain.port.ReservationIntentStore reservationIntentStore;

    @Autowired
    private ReservationReconciliationJob reservationReconciliationJob;

    @Autowired
    private RedisReservationAdapter redisReservationAdapter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailureInjectingStockCachePort failureInjectingStockCachePort;

    @Autowired
    private FailOnceOrderCreatePublisher failOnceOrderCreatePublisher;

    @BeforeEach
    void clearRedisAndRabbitState() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        for (String queue : List.of(
                RabbitTopologyConfig.ORDER_CREATE_QUEUE,
                RabbitTopologyConfig.ORDER_HOLD_TTL_QUEUE,
                RabbitTopologyConfig.ORDER_EXPIRE_QUEUE,
                RabbitTopologyConfig.OUTBOX_ORDER_CREATED_QUEUE,
                RabbitTopologyConfig.OUTBOX_ORDER_EXPIRED_QUEUE,
                RabbitTopologyConfig.OUTBOX_PAYMENT_SUCCESS_QUEUE)) {
            rabbitAdmin.purgeQueue(queue, true);
        }
        failureInjectingStockCachePort.reset();
        failOnceOrderCreatePublisher.reset();
    }

    @Test
    void fullApiLifecycleShowsCreatingThenPendingAndPaysIdempotently() throws Exception {
        Fixture fixture = prepareSale(5, 10, 3600);
        MvcResult reservation = reserve(fixture, fixture.userId(), 2);
        UUID orderId = UUID.fromString(json(reservation).path("orderId").asText());

        assertThat(json(reservation).path("status").asText()).isEqualTo("CREATING");
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .with(jwtFor(fixture.userId(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATING"));
        assertThat(redisReservationAdapter.getAvailableStock(fixture.ticketTypeId())).isEqualTo(3);

        mockMvc.perform(post("/api/v1/admin/reservations/relay")
                        .with(jwtFor(fixture.userId(), "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/reservations/relay"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/reservations/relay")
                        .with(jwtFor(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(1));
        mockMvc.perform(post("/api/v1/admin/reservations/relay")
                        .with(jwtFor(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(0));
        awaitOrderStatus(orderId, fixture.userId(), "PENDING_PAYMENT");
        MvcResult pending = mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .with(jwtFor(fixture.userId(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentProvider").value("LOCAL"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andReturn();

        JsonNode pendingBody = json(pending);
        String transactionRef = pendingBody.path("paymentTransactionRef").asText();
        BigDecimal amount = pendingBody.path("totalAmount").decimalValue();
        Map<String, Object> callback = callbackBody(transactionRef, amount, true);

        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor(fixture.userId(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.ticketCount").value(2));

        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor(fixture.userId(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.outcome").value("ALREADY_SUCCESS"))
                .andExpect(jsonPath("$.ticketCount").value(2));

        var order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getItems()).hasSize(1);
        assertThat(paymentRepository.findByOrderId(orderId)).hasValueSatisfying(payment -> {
            assertThat(payment.getProvider()).isEqualTo(PaymentProvider.LOCAL);
            assertThat(payment.getStatus().name()).isEqualTo("SUCCESS");
        });
        assertThat(ticketRepository.findAllByOrderItemId(order.getItems().get(0).getId())).hasSize(2);
        assertThat(ticketTypeRepository.findById(fixture.ticketTypeId()).orElseThrow().getSoldQuantity())
                .isEqualTo(2);
        assertThat(countOutbox(orderId, "PAYMENT_SUCCESS")).isEqualTo(1);
    }

    @Test
    void concurrentHttpReservationsNeverExceedWarmedStock() throws Exception {
        Fixture fixture = prepareSale(6, 40, 3600);
        int attempts = 30;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<MvcResult>> requests = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                requests.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent reservations");
                    }
                    return reserve(fixture, fixture.userId(), 1);
                }));
            }
            start.countDown();

            int accepted = 0;
            int rejected = 0;
            Set<UUID> acceptedOrderIds = new java.util.HashSet<>();
            for (Future<MvcResult> request : requests) {
                MvcResult result = request.get(30, TimeUnit.SECONDS);
                if (result.getResponse().getStatus() == 202) {
                    accepted++;
                    acceptedOrderIds.add(UUID.fromString(json(result).path("orderId").asText()));
                } else if (result.getResponse().getStatus() == 409) {
                    rejected++;
                } else {
                    throw new AssertionError("Unexpected reservation HTTP status: "
                            + result.getResponse().getStatus() + " "
                            + result.getResponse().getContentAsString());
                }
            }

            assertThat(accepted).isEqualTo(6);
            assertThat(rejected).isEqualTo(attempts - 6);
            assertThat(acceptedOrderIds).hasSize(accepted);
            assertThat(redisReservationAdapter.getAvailableStock(fixture.ticketTypeId())).isZero();
            assertThat(failureInjectingStockCachePort.minimumObservedStock())
                    .isGreaterThanOrEqualTo(0)
                    .isLessThan(Integer.MAX_VALUE);

            assertThat(reservationIntentRelay.relayOnce()).isEqualTo(accepted);
            awaitOrderCount(fixture.eventId(), accepted);
            assertThat(countOrders(fixture.eventId())).isEqualTo(accepted);
            assertThat(countOrderItems(fixture.eventId())).isEqualTo(accepted);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void publishFailureKeepsReservationRecoverableAndRetryCreatesOneOrder() throws Exception {
        Fixture fixture = prepareSale(1, 10, 3600);
        failOnceOrderCreatePublisher.failNextPublish();
        MvcResult reservation = reserve(fixture, fixture.userId(), 1);
        UUID orderId = UUID.fromString(json(reservation).path("orderId").asText());

        assertThat(reservationIntentRelay.relayOnce()).isZero();
        ReservationIntent retriedIntent = reservationIntentStore.findByOrderId(orderId).orElseThrow();
        assertThat(retriedIntent.retryCount()).isEqualTo(1);
        assertThat(orderRepository.findById(orderId)).isEmpty();
        assertThat(redisReservationAdapter.getAvailableStock(fixture.ticketTypeId())).isZero();

        Awaitility.await().pollInterval(Duration.ofMillis(25)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(reservationIntentRelay.relayOnce()).isEqualTo(1));
        awaitOrderStatus(orderId, fixture.userId(), "PENDING_PAYMENT");
        assertThat(countOrders(fixture.eventId())).isEqualTo(1);
        assertThat(countOrderItems(fixture.eventId())).isEqualTo(1);
        assertThat(redisReservationAdapter.getAvailableStock(fixture.ticketTypeId())).isZero();
        assertThat(failOnceOrderCreatePublisher.publishAttempts()).isEqualTo(2);
    }

    @Test
    void failedRedisReleaseIsRecoveredByReconciliation() throws Exception {
        Fixture fixture = prepareSale(1, 10, 8);
        failureInjectingStockCachePort.failNextRelease();
        MvcResult reservation = reserve(fixture, fixture.userId(), 1);
        UUID orderId = UUID.fromString(json(reservation).path("orderId").asText());
        assertThat(reservationIntentRelay.relayOnce()).isEqualTo(1);
        awaitOrderStatus(orderId, fixture.userId(), "PENDING_PAYMENT");

        Awaitility.await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(18))
                .untilAsserted(() -> {
                    assertThat(orderRepository.findById(orderId)).hasValueSatisfying(order ->
                            assertThat(order.getStatus().name()).isEqualTo("EXPIRED"));
                    assertThat(failureInjectingStockCachePort.releaseAttempts()).isEqualTo(1);
                });

        assertThat(redisReservationAdapter.getAvailableStock(fixture.ticketTypeId())).isZero();
        assertThat(redisTemplate.opsForValue().get(
                RedisReservationKeys.userLimit(fixture.ticketTypeId(), fixture.userId())))
                .isEqualTo("1");

        assertThat(reservationReconciliationJob.reconcileOnce()).isGreaterThan(0);
        Awaitility.await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(redisReservationAdapter.getAvailableStock(fixture.ticketTypeId())).isEqualTo(1);
                    assertThat(redisTemplate.opsForValue().get(
                            RedisReservationKeys.userLimit(fixture.ticketTypeId(), fixture.userId())))
                            .isEqualTo("0");
                    assertThat(failureInjectingStockCachePort.releaseAttempts()).isEqualTo(2);
                });

        assertThat(countOutbox(orderId, "ORDER_EXPIRED")).isEqualTo(1);
        assertThat(ticketTypeRepository.findById(fixture.ticketTypeId()).orElseThrow().getSoldQuantity())
                .isZero();
    }

    @Test
    void paymentWinningBeforeTtlKeepsPaidOrderAndDoesNotReleaseInventory() throws Exception {
        Fixture fixture = prepareSale(1, 10, 8);
        MvcResult reservation = reserve(fixture, fixture.userId(), 1);
        UUID orderId = UUID.fromString(json(reservation).path("orderId").asText());
        assertThat(reservationIntentRelay.relayOnce()).isEqualTo(1);
        awaitOrderStatus(orderId, fixture.userId(), "PENDING_PAYMENT");
        JsonNode pending = json(mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                .with(jwtFor(fixture.userId(), "CUSTOMER"))).andReturn());
        Map<String, Object> callback = callbackBody(
                pending.path("paymentTransactionRef").asText(),
                pending.path("totalAmount").decimalValue(),
                true);

        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor(fixture.userId(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAID"));

        Instant expiresAt = Instant.parse(pending.path("expiresAt").asText());
        Awaitility.await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(18))
                .untilAsserted(() -> {
                    assertThat(Instant.now()).isAfter(expiresAt.plusSeconds(1));
                    assertThat(rabbitAdmin.getQueueInfo(RabbitTopologyConfig.ORDER_HOLD_TTL_QUEUE)
                            .getMessageCount()).isZero();
                    assertThat(orderRepository.findById(orderId)).hasValueSatisfying(order ->
                            assertThat(order.getStatus().name()).isEqualTo("PAID"));
                    assertThat(redisReservationAdapter.getAvailableStock(fixture.ticketTypeId())).isZero();
                    assertThat(ticketTypeRepository.findById(fixture.ticketTypeId()).orElseThrow()
                            .getSoldQuantity()).isEqualTo(1);
                });

        assertThat(ticketRepository.findAllByOrderItemId(
                orderRepository.findById(orderId).orElseThrow().getItems().get(0).getId())).hasSize(1);
        assertThat(countOutbox(orderId, "PAYMENT_SUCCESS")).isEqualTo(1);
        assertThat(countOutbox(orderId, "ORDER_EXPIRED")).isZero();
    }

    @Test
    void startSaleEndpointEnforcesEventOwnershipAndWarmsInventory() throws Exception {
        Fixture fixture = prepareUnstartedSale(4, 10, 3600);

        mockMvc.perform(post("/api/v1/events/{eventId}/start-sale", fixture.eventId())
                        .with(jwtFor(UUID.randomUUID(), "ORGANIZER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/events/{eventId}/start-sale", fixture.eventId())
                        .with(jwtFor(fixture.userId(), "CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/events/{eventId}/start-sale", fixture.eventId())
                        .with(jwtFor(fixture.organizerId(), "ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_SALE"));

        assertThat(eventRepository.findById(fixture.eventId()).orElseThrow().getStatus())
                .isEqualTo(EventStatus.ON_SALE);
        assertThat(redisReservationAdapter.getAvailableStock(fixture.ticketTypeId())).isEqualTo(4);

        Fixture adminFixture = prepareUnstartedSale(5, 10, 3600);
        mockMvc.perform(post("/api/v1/events/{eventId}/start-sale", adminFixture.eventId())
                        .with(jwtFor(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_SALE"));
        assertThat(redisReservationAdapter.getAvailableStock(adminFixture.ticketTypeId())).isEqualTo(5);
    }

    @Test
    void generatedOpenApiDocumentsBearerSecurityForPurchaseRoutes() throws Exception {
        assertThat(applicationContext.getBeansOfType(OrderCreateListener.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(OrderExpiryListener.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ReservationReconciliationJob.class)).hasSize(1);
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(hasBearerRequirement(document.at("/paths/~1api~1v1~1orders/post/security"))).isTrue();
        assertThat(hasBearerRequirement(document.at("/paths/~1api~1v1~1orders~1{orderId}/get/security")))
                .isTrue();
        assertThat(hasBearerRequirement(
                document.at("/paths/~1api~1v1~1payments~1callback/post/security"))).isTrue();
        assertThat(hasBearerRequirement(document.at(
                "/paths/~1api~1v1~1events~1{id}~1start-sale/post/security"))).isTrue();
        assertThat(hasBearerRequirement(document.at(
                "/paths/~1api~1v1~1admin~1reservations~1relay/post/security"))).isTrue();
    }

    private Fixture prepareSale(int stock, int maxPerUser, int holdDurationSec) throws Exception {
        Fixture fixture = prepareUnstartedSale(stock, maxPerUser, holdDurationSec);
        mockMvc.perform(post("/api/v1/events/{eventId}/start-sale", fixture.eventId())
                        .with(jwtFor(fixture.organizerId(), "ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_SALE"));
        assertThat(eventRepository.findById(fixture.eventId()).orElseThrow().getStatus())
                .isEqualTo(EventStatus.ON_SALE);
        assertThat(redisReservationAdapter.getAvailableStock(fixture.ticketTypeId())).isEqualTo(stock);
        return fixture;
    }

    private Fixture prepareUnstartedSale(int stock, int maxPerUser, int holdDurationSec) {
        UUID userId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        userRepository.save(User.register(
                userId,
                "buyer-" + userId + "@example.test",
                null,
                "test-only-password-hash",
                "E2E Buyer",
                UserRole.CUSTOMER));
        userRepository.save(User.register(
                organizerId,
                "organizer-" + organizerId + "@example.test",
                null,
                "test-only-password-hash",
                "E2E Organizer",
                UserRole.ORGANIZER));

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        Instant now = Instant.now();
        Event event = Event.create(
                eventId,
                organizerId,
                "Ticket purchase E2E " + eventId,
                "Full stack test fixture",
                "Test venue",
                now.plus(Duration.ofDays(2)),
                now.plus(Duration.ofDays(2)).plus(Duration.ofHours(3)),
                now.minus(Duration.ofMinutes(1)),
                now.plus(Duration.ofDays(1)));
        event.publish();
        eventRepository.save(event);
        ticketTypeRepository.save(TicketType.create(
                ticketTypeId,
                eventId,
                "Standard",
                Money.of(new BigDecimal("125000.00")),
                stock,
                maxPerUser,
                holdDurationSec));

        return new Fixture(userId, organizerId, eventId, ticketTypeId);
    }

    private MvcResult reserve(Fixture fixture, UUID userId, int quantity) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", fixture.eventId());
        body.put("ticketTypeId", fixture.ticketTypeId());
        body.put("quantity", quantity);
        return mockMvc.perform(post("/api/v1/orders")
                        .with(jwtFor(userId, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private void awaitOrderStatus(UUID orderId, UUID userId, String expectedStatus) {
        Awaitility.await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(12))
                .untilAsserted(() -> mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                                .with(jwtFor(userId, "CUSTOMER")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value(expectedStatus)));
    }

    private void awaitOrderCount(UUID eventId, int expectedCount) {
        Awaitility.await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(countOrders(eventId)).isEqualTo(expectedCount));
    }

    private int countOrders(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from orders where event_id = ?", Integer.class, eventId);
    }

    private int countOrderItems(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from order_items oi join orders o on o.id = oi.order_id where o.event_id = ?",
                Integer.class,
                eventId);
    }

    private int countOutbox(UUID orderId, String eventType) {
        return jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where event_type = ? "
                        + "and (aggregate_id = ? or payload like ?)",
                Integer.class,
                eventType,
                orderId,
                "%" + orderId + "%");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> callbackBody(String transactionRef, BigDecimal amount, boolean success) {
        Map<String, Object> callback = new LinkedHashMap<>();
        callback.put("transactionRef", transactionRef);
        callback.put("provider", "LOCAL");
        callback.put("amount", amount);
        callback.put("success", success);
        return callback;
    }

    private static boolean hasBearerRequirement(JsonNode security) {
        for (JsonNode requirement : security) {
            if (requirement.has("bearerAuth")) {
                return true;
            }
        }
        return false;
    }

    private static RequestPostProcessor jwtFor(UUID subject, String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(subject.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private record Fixture(UUID userId, UUID organizerId, UUID eventId, UUID ticketTypeId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureInjectionConfiguration {

        @Bean
        @Primary
        FailureInjectingStockCachePort failureInjectingStockCachePort(RedisReservationAdapter delegate) {
            return new FailureInjectingStockCachePort(delegate);
        }

        @Bean
        @Primary
        FailOnceOrderCreatePublisher failOnceOrderCreatePublisher(
                RabbitTemplate rabbitTemplate,
                MessagingProperties properties,
                OrderCreateMessageCodec codec) {
            return new FailOnceOrderCreatePublisher(rabbitTemplate, properties, codec);
        }
    }

    static final class FailureInjectingStockCachePort implements StockCachePort {

        private final RedisReservationAdapter delegate;
        private final AtomicBoolean failNextRelease = new AtomicBoolean();
        private final AtomicInteger releaseAttempts = new AtomicInteger();
        private final AtomicInteger minimumObservedStock = new AtomicInteger(Integer.MAX_VALUE);

        FailureInjectingStockCachePort(RedisReservationAdapter delegate) {
            this.delegate = delegate;
        }

        @Override
        public com.tienphat.domain.port.ReservationResult tryReserve(
                com.tienphat.domain.port.ReservationRequest request) {
            com.tienphat.domain.port.ReservationResult result = delegate.tryReserve(request);
            int available = delegate.getAvailableStock(request.ticketTypeId());
            minimumObservedStock.accumulateAndGet(available, Math::min);
            return result;
        }

        @Override
        public void release(UUID orderId, UUID ticketTypeId, UUID userId, int quantity) {
            releaseAttempts.incrementAndGet();
            if (failNextRelease.compareAndSet(true, false)) {
                throw new IllegalStateException("Injected Redis release failure for test");
            }
            delegate.release(orderId, ticketTypeId, userId, quantity);
        }

        @Override
        public int getAvailableStock(UUID ticketTypeId) {
            return delegate.getAvailableStock(ticketTypeId);
        }

        @Override
        public void warmUp(UUID ticketTypeId, int totalQuantity) {
            delegate.warmUp(ticketTypeId, totalQuantity);
        }

        void failNextRelease() {
            failNextRelease.set(true);
        }

        int releaseAttempts() {
            return releaseAttempts.get();
        }

        int minimumObservedStock() {
            return minimumObservedStock.get();
        }

        void reset() {
            failNextRelease.set(false);
            releaseAttempts.set(0);
            minimumObservedStock.set(Integer.MAX_VALUE);
        }
    }

    static final class FailOnceOrderCreatePublisher extends OrderCreatePublisher {

        private final AtomicBoolean failNextPublish = new AtomicBoolean();
        private final AtomicInteger publishAttempts = new AtomicInteger();

        FailOnceOrderCreatePublisher(
                RabbitTemplate rabbitTemplate,
                MessagingProperties properties,
                OrderCreateMessageCodec codec) {
            super(rabbitTemplate, properties, codec);
        }

        @Override
        public OrderCreateMessage publish(ReservationIntent intent) {
            publishAttempts.incrementAndGet();
            if (failNextPublish.compareAndSet(true, false)) {
                throw new IllegalStateException("Injected RabbitMQ publish failure for test");
            }
            return super.publish(intent);
        }

        void failNextPublish() {
            failNextPublish.set(true);
        }

        int publishAttempts() {
            return publishAttempts.get();
        }

        void reset() {
            failNextPublish.set(false);
            publishAttempts.set(0);
        }
    }
}

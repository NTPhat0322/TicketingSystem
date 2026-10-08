package com.tienphat.ticketingsystem.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.PaymentProvider;
import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.port.ReservationRequest;
import com.tienphat.domain.port.ReservationResult;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.PaymentRepository;
import com.tienphat.domain.repository.TicketRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.vo.Money;
import com.tienphat.infrastructure.redis.RedisReservationAdapter;
import com.tienphat.infrastructure.redis.RedisReservationKeys;
import com.tienphat.ticketingsystem.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PaymentApiSmokeTest extends AbstractPostgresIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
    private static final Money UNIT_PRICE = Money.of(new BigDecimal("150000.00"));
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisReservationAdapter redisReservationAdapter;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void clearRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    @Transactional
    void localCallbackIsIdempotentAndIssuesTicketsOnce() throws Exception {
        Fixture fixture = createFixture(2, true, false);
        Order order = fixture.order();
        Payment payment = fixture.payment();
        Map<String, Object> callback = callbackBody(fixture);

        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor(USER_ID, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.ticketCount").value(2));

        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor(USER_ID, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.outcome").value("ALREADY_SUCCESS"))
                .andExpect(jsonPath("$.ticketCount").value(2));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus().name())
                .isEqualTo("PAID");
        assertThat(paymentRepository.findByTransactionRef(payment.getTransactionRef()).orElseThrow()
                .getStatus().name()).isEqualTo("SUCCESS");
        assertThat(ticketRepository.findAllByOrderItemId(order.getItems().get(0).getId())).hasSize(2);
        assertThat(ticketTypeRepository.findById(fixture.ticketTypeId()).orElseThrow().getSoldQuantity())
                .isEqualTo(2);
        assertThat(redisTemplate.hasKey(RedisReservationKeys.hold(order.getId()))).isFalse();
        assertThat(redisTemplate.opsForValue().get(
                RedisReservationKeys.userLimit(fixture.ticketTypeId(), USER_ID))).isEqualTo("2");
    }

    @Test
    @Transactional
    void failedCallbackReturnsAcceptedFailureAndReleasesReservation() throws Exception {
        Fixture fixture = createFixture(1, true, false);

        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor(USER_ID, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(callbackBody(fixture, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("FAILED"))
                .andExpect(jsonPath("$.outcome").value("FAILED"))
                .andExpect(jsonPath("$.ticketCount").value(0));

        assertThat(redisReservationAdapter.getAvailableStock(fixture.ticketTypeId())).isEqualTo(10);
        assertThat(redisTemplate.opsForValue().get(
                RedisReservationKeys.userLimit(fixture.ticketTypeId(), USER_ID))).isEqualTo("0");
    }

    @Test
    @Transactional
    void wrongOwnerIsForbiddenAndExpiredOrderCannotBePaid() throws Exception {
        Fixture ownedFixture = createFixture(1, false, false);
        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor(UUID.randomUUID(), "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(callbackBody(ownedFixture))))
                .andExpect(status().isForbidden());

        Fixture expiredFixture = createFixture(1, false, true);
        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor(USER_ID, "CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(callbackBody(expiredFixture))))
                .andExpect(status().isConflict());
    }

    private Fixture createFixture(int quantity, boolean reserve, boolean expired) {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        Instant reservedAt = expired ? Instant.now().minusSeconds(600) : Instant.now();
        Instant expiresAt = expired ? Instant.now().minusSeconds(300) : reservedAt.plusSeconds(300);
        Order order = Order.create(
                Order.generateId(),
                "ORD-API-" + UUID.randomUUID(),
                USER_ID,
                eventId,
                reservedAt,
                expiresAt);
        order.addItem(ticketTypeId, quantity, UNIT_PRICE);
        if (expired) {
            order.expire();
        }
        if (reserve) {
            redisReservationAdapter.warmUp(ticketTypeId, 10);
            ReservationResult result = redisReservationAdapter.tryReserve(new ReservationRequest(
                    order.getId(),
                    USER_ID,
                    eventId,
                    ticketTypeId,
                    quantity,
                    UNIT_PRICE,
                    5,
                    300,
                    reservedAt,
                    expiresAt));
            assertThat(result).isEqualTo(ReservationResult.SUCCESS);
        }
        orderRepository.save(order);
        ticketTypeRepository.save(TicketType.create(
                ticketTypeId, eventId, "Standard", UNIT_PRICE, 10, 5, 300));
        Payment payment = paymentRepository.save(Payment.initiate(
                order.getId(), PaymentProvider.LOCAL, order.getTotalAmount(), "LOCAL-" + order.getId()));
        return new Fixture(order, payment, ticketTypeId);
    }

    private Map<String, Object> callbackBody(Fixture fixture) {
        return callbackBody(fixture, true);
    }

    private Map<String, Object> callbackBody(Fixture fixture, boolean success) {
        Map<String, Object> callback = new LinkedHashMap<>();
        callback.put("transactionRef", fixture.payment().getTransactionRef());
        callback.put("provider", "LOCAL");
        callback.put("amount", fixture.order().getTotalAmount().getAmount());
        callback.put("success", success);
        return callback;
    }

    private record Fixture(Order order, Payment payment, UUID ticketTypeId) {
    }

    private static RequestPostProcessor jwtFor(UUID subject, String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(subject.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
